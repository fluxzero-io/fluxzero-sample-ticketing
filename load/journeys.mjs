import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Browser, HttpError } from './browser.mjs';

const base = new URL(process.argv[2] || 'http://localhost:63024');
assert(['localhost', '127.0.0.1', '[::1]'].includes(base.hostname), 'This runner creates demo sales: use a local environment');
assert.equal(base.pathname, '/');
const concurrency = Number(process.argv[3] || 8);
assert(Number.isInteger(concurrency) && concurrency >= 1 && concurrency <= 256, 'Concurrency must be 1–256');
const run = `http-load-${randomUUID()}`;
const operator = await new Browser(base.origin).login('demo-organizer');
const customers = await pool(Array.from({ length: concurrency }, (_, i) => i), 8,
  i => new Browser(base.origin).login(`${run}-${i}`));
const catalog = await operator.get('/api/operations/catalog');
const reports = [];
const performances = [];
console.log(JSON.stringify({ run, base: base.origin, concurrency, node: process.version }));

// All operations, including setup and outcome checks, use the application's normal HTTP API.

async function pool(items, limit, action) {
  let next = 0;
  const results = new Array(items.length), errors = [];
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const index = next++;
      try { results[index] = await action(items[index], index); }
      catch (error) { errors.push(error); }
    }
  }));
  if (errors.length) throw new AggregateError(errors, `${errors.length} unexpected outcomes: ${errors.slice(0, 3).map(e => e.message).join('; ')}`);
  return results;
}

async function wave(name, items, action) {
  const latencies = [], started = performance.now(), requestsBefore = Browser.completedRequests;
  let failures = 0;
  try {
    return await pool(items, concurrency, async (item, index) => {
      const before = performance.now();
      try { return await action(item, index); }
      catch (error) { failures++; throw error; }
      finally { latencies.push(performance.now() - before); }
    });
  } finally {
    latencies.sort((a, b) => a - b);
    const elapsedMs = performance.now() - started;
    const percentile = p => Math.round(latencies[Math.max(0, Math.ceil(latencies.length * p) - 1)] || 0);
    const report = { name, journeys: items.length, failures, httpResponses: Browser.completedRequests - requestsBefore, elapsedMs: Math.round(elapsedMs),
      completedPerSecond: Math.round(items.length * 1000 / elapsedMs),
      p50Ms: percentile(.5), p95Ms: percentile(.95), p99Ms: percentile(.99), maxMs: percentile(1) };
    reports.push(report); console.log(JSON.stringify(report));
  }
}

async function schedule(planId) {
  const plan = catalog.plans.find(p => p.id === planId);
  assert(plan, `Missing demo plan ${planId}`);
  const id = randomUUID();
  const startsAt = new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10) + 'T20:00:00';
  assert.equal(await operator.post('/api/operations/performances', {
    performanceId: id, eventId: 'after-hours', seatingPlanId: planId, startsAt,
    sectionPrices: Object.fromEntries(plan.sections.map(s => [s.id, { minorUnits: 2500, currency: 'EUR' }])),
    ticketTypes: [{ id: 'standard', name: 'Standard', eligibility: 'All visitors', discountPercent: 0 }],
  }), id);
  performances.push(id);
  return id;
}
const selection = (sectionId, quantity = 2) => Array.from({ length: quantity }, () =>
  ({ sectionId, seatId: null, ticketType: 'standard', wheelchairAccessRequired: false }));
const availability = id => customers[0].get(`/api/programme/${id}/availability`);
const order = id => operator.get(`/api/operations/orders/${id}`);
const remaining = async id => (await availability(id)).sections.find(s => s.id === 'floor').remaining;

async function block(id, positions) {
  const productionHoldId = randomUUID();
  await operator.post(`/api/operations/performances/${id}/allocations`, {
    productionHoldId, details: { reason: `Production allocation ${run}` },
    positions,
  });
  return productionHoldId;
}
async function prepareSeats() {
  const id = await schedule('concertgebouw-recital-2023-07');
  const seats = [];
  for (let offset = 0; ; offset += 100) {
    const page = await customers[0].get(`/api/programme/${id}/seats?section=stalls&offset=${offset}`);
    seats.push(...page.seats.map(s => s.seat));
    if (!page.hasMore) break;
  }
  const saleSeats = seats.filter(s => s.kind === 'STANDARD').slice(0, 80);
  assert.equal(saleSeats.length, 80);
  const withheld = seats.filter(s => !saleSeats.includes(s));
  for (let offset = 0; offset < withheld.length; offset += 100) {
    await block(id, withheld.slice(offset, offset + 100).map(s => ({ sectionId: 'stalls', seatId: s.id, quantity: 1 })));
  }
  const positions = index => saleSeats.slice((index % 40) * 2, (index % 40) * 2 + 2).map(s =>
    ({ sectionId: 'stalls', seatId: s.id, ticketType: 'standard', wheelchairAccessRequired: false }));
  const stock = async () => (await availability(id)).sections.find(s => s.id === 'stalls').remaining;
  assert.equal(await stock(), 80);
  return { id, positions, stock };
}
async function reserve(id, customer, positions = selection('floor'), cash = false) {
  const reservationId = randomUUID();
  const body = { reservationId, performanceId: id, selection: positions };
  const result = cash
    ? await operator.post('/api/operations/box-office/reservations', { ...body, customerId: customer.subject })
    : await customer.post('/api/reservations', body);
  assert.equal(result, reservationId);
  return { id: reservationId, customer, cash };
}
async function capacityRefusal(action, message = 'Section capacity exceeded') {
  try { return await action(); }
  catch (error) {
    // Reject technical errors, authentication failures and unrelated validation errors.
    if (error instanceof HttpError && error.status === 403 && error.body === message) return null;
    throw error;
  }
}
async function cancel(booking) {
  if (booking.cash) await operator.post(`/api/operations/orders/${booking.id}/cancel`);
  else await booking.customer.post(`/api/reservations/${booking.id}/cancel`);
}
async function receipt(booking) {
  const reference = `${run}-${booking.id}`;
  await operator.post('/api/operations/box-office/payments', {
    reservationId: booking.id, method: 'CASH', reference, amount: { minorUnits: 5000, currency: 'EUR' },
  });
}
async function refund(booking) {
  const view = await order(booking.id);
  assert.equal(view.payments.length, 1);
  const payment = view.payments[0];
  assert.equal(payment.payment.status, 'REFUND_REQUIRED');
  assert.equal(payment.payment.captured.minorUnits, 5000);
  assert.equal(payment.pendingRepayment.amount.minorUnits, 5000);
  await operator.post('/api/operations/box-office/refunds', {
    refundId: payment.pendingRepayment.refundId, reference: `${run}-refund-${booking.id}`,
  });
  const completed = (await order(booking.id)).payments[0];
  assert.equal(completed.payment.status, 'REFUNDED');
  assert.equal(completed.payment.captured.minorUnits, 5000);
  assert.equal(completed.payment.refundedAmount, 5000);
  assert(completed.boxOfficeReceipt, 'Original cash receipt must remain visible');
  assert.equal(completed.repayments.length, 1);
  assert(completed.repayments[0].completedAt);
}
async function assertBooking(booking, status) {
  const purchase = await booking.customer.get(`/api/reservations/${booking.id}`);
  assert.equal(purchase.reservation.status, status);
  assert.equal(purchase.reservation.customerId, booking.customer.subject);
  assert.equal(purchase.reservation.admissions.length, 2);
  assert.equal(purchase.reservation.total.minorUnits, 5000);
  return purchase;
}
async function assertOrders(id, expected) {
  // Search-backed lists may catch up after a write; retry reads only, never mutations.
  let found = [];
  for (let attempt = 0; attempt < 40; attempt++) {
    found = [];
    for (let offset = 0; ; offset += 20) {
      const page = await operator.get(`/api/operations/performances/${id}/orders?offset=${offset}`);
      found.push(...page.items.map(v => v.reservation.reservationId));
      if (!page.hasMore) break;
    }
    if (found.length >= expected.length) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.deepEqual(found.sort(), expected.map(b => b.id).sort(), 'Every stored order must have a successful HTTP outcome');
}
async function standingSale() {
  const id = await schedule('tivoli-ronda-demo-v1');
  const capacity = await remaining(id);
  assert.equal(capacity % 2, 0);
  const count = Math.max(128, concurrency * 8);
  const outcomes = await wave('browse-and-reserve', Array.from({ length: count }), async (_, i) => {
    const customer = customers[i % customers.length];
    await customer.get('/api/programme');
    await customer.get(`/api/programme/${id}`);
    await customer.get(`/api/programme/${id}/availability`);
    return capacityRefusal(() => reserve(id, customer));
  });
  const bookings = outcomes.filter(Boolean);
  assert.equal(bookings.length, capacity / 2); assert.equal(await remaining(id), 0);
  await pool(bookings, concurrency, b => assertBooking(b, 'HELD'));
  await assertOrders(id, bookings);
  const released = bookings.slice(0, 2);
  await wave('customer-cancellation', released, cancel);
  assert.equal(await remaining(id), released.length * 2);
  const resale = (await wave('resale', Array.from({ length: 20 }), (_, i) =>
    capacityRefusal(() => reserve(id, customers[i % customers.length])))).filter(Boolean);
  assert.equal(resale.length, released.length); assert.equal(await remaining(id), 0);
  await pool(released, concurrency, b => assertBooking(b, 'CANCELLED'));
  await assertOrders(id, [...bookings, ...resale]);
  await pool([...bookings.slice(released.length), ...resale], concurrency, cancel);
  assert.equal(await remaining(id), capacity);
  console.log(JSON.stringify({ scenario: 'standing-audit', capacity, accepted: bookings.length, refused: count - bookings.length, resold: resale.length }));
}
async function mixedSales() {
  const { id, positions, stock } = await prepareSeats();
  const before = await pool(Array.from({ length: 20 }), 8, (_, i) => reserve(id, customers[i % customers.length], positions(i), true));
  await pool(before.slice(0, 10), 8, receipt);
  const online = [], cash = [], allocations = [];
  // Interleave customer demand with cashier receipts/cancellations and production holds.
  const work = [];
  for (let i = 0; i < 64; i++) {
    const customer = customers[i % customers.length];
    if (i < 20) work.push(async () => cancel(before[i]));
    if (i >= 10 && i < 20) work.push(async () => receipt(before[i]));
    work.push(async () => { const b = await capacityRefusal(() => reserve(id, customer, positions(i + 20)), 'Seat is unavailable'); if (b) online.push(b); });
    if (i < 32) work.push(async () => {
      const b = await capacityRefusal(() => reserve(id, customer, positions(i + 30), true), 'Seat is unavailable');
      if (b) { cash.push(b); await receipt(b); }
    });
    if (i < 16) work.push(async () => {
      const hold = await capacityRefusal(() => block(id, positions(i + 25).map(p => ({ sectionId: p.sectionId, seatId: p.seatId, quantity: 1 }))), 'Seat is unavailable for this allocation');
      if (hold) allocations.push(hold);
    });
  }
  await wave('mixed-sales-and-cancellation', work, action => action());
  assert.equal(await stock(), 80 - 2 * (online.length + cash.length + allocations.length));
  await pool(online, concurrency, b => assertBooking(b, 'HELD'));
  await pool(cash, concurrency, async b => {
    const purchase = await assertBooking(b, 'CONFIRMED');
    assert.equal(purchase.tickets.filter(t => t.status === 'VALID').length, 2);
    assert.equal(purchase.payments.length, 1);
    assert.equal(purchase.payments[0].captured.minorUnits, 5000);
  });
  await pool(before, concurrency, async b => {
    const purchase = await assertBooking(b, 'CANCELLED');
    assert.equal(purchase.tickets.filter(t => t.status === 'VALID').length, 0);
    await refund(b);
  });
  await assertOrders(id, [...before, ...online, ...cash]);
  await pool([...online, ...cash], concurrency, cancel);
  await pool(cash, concurrency, refund);
  await pool(allocations, concurrency, hold => operator.post(`/api/operations/allocations/${hold}/release`));
  assert.equal(await stock(), 80);
  const replacement = await reserve(id, customers[0], positions(0));
  assert.equal(await stock(), 78); await cancel(replacement);
  console.log(JSON.stringify({ scenario: 'mixed-audit', cancelledCaptures: before.length,
    newOnline: online.length, newCash: cash.length, productionHolds: allocations.length,
    capturedMinorUnits: (before.length + cash.length) * 5000,
    refundedMinorUnits: (before.length + cash.length) * 5000, remaining: await stock() }));
}
async function seatedSale() {
  const id = await schedule('concertgebouw-recital-2023-07');
  const stock = await availability(id);
  const pairs = [];
  for (const section of stock.sections) {
    const seats = [];
    for (let offset = 0; ; offset += 100) {
      const page = await customers[0].get(`/api/programme/${id}/seats?section=${section.id}&offset=${offset}`);
      seats.push(...page.seats.map(s => s.seat).filter(s => s.kind === 'STANDARD'));
      if (!page.hasMore) break;
    }
    for (let i = 0; i + 1 < seats.length; i += 2) pairs.push(seats.slice(i, i + 2).map(seat =>
      ({ sectionId: section.id, seatId: seat.id, ticketType: 'standard', wheelchairAccessRequired: false })));
  }
  const count = Math.max(pairs.length * 2, concurrency * 8);
  const outcomes = await wave('browse-seats-and-reserve', Array.from({ length: count }), async (_, i) => {
    const customer = customers[i % customers.length], positions = pairs[i % pairs.length];
    await customer.get(`/api/programme/${id}`);
    await customer.get(`/api/programme/${id}/availability`);
    await customer.get(`/api/programme/${id}/seats?section=${positions[0].sectionId}`);
    return capacityRefusal(() => reserve(id, customer, positions), 'Seat is unavailable');
  });
  const bookings = outcomes.filter(Boolean);
  assert.equal(bookings.length, pairs.length);
  await pool(bookings, concurrency, async b => {
    const purchase = await assertBooking(b, 'HELD');
    const index = outcomes.indexOf(b);
    assert.deepEqual(purchase.reservation.admissions.map(a => a.seatId), pairs[index % pairs.length].map(p => p.seatId));
  });
  for (const section of (await availability(id)).sections) {
    const sold = pairs.filter(p => p[0].sectionId === section.id).length * 2;
    assert.equal(section.remaining, stock.sections.find(s => s.id === section.id).remaining - sold);
  }
  await assertOrders(id, bookings);
  await wave('seated-cancellation', bookings, cancel);
  assert.deepEqual((await availability(id)).sections.map(s => s.remaining), stock.sections.map(s => s.remaining));
  console.log(JSON.stringify({ scenario: 'seated-audit', acceptedGroups: bookings.length, refused: count - bookings.length,
    releasedSeats: bookings.length * 2 }));
}
async function reservedSeats() {
  const id = await schedule('concertgebouw-recital-2023-07');
  const page = await customers[0].get(`/api/programme/${id}/suggestions?section=stalls&quantity=2`);
  const group = page.groups[0];
  assert(group, 'Expected an adjacent pair');
  const positions = group.map(s => ({ sectionId: 'stalls', seatId: s.id, ticketType: 'standard', wheelchairAccessRequired: false }));
  const outcomes = await wave('same-adjacent-seats', Array.from({ length: Math.max(16, concurrency * 2) }), (_, i) =>
    capacityRefusal(() => reserve(id, customers[i % customers.length], positions), 'Seat is unavailable'));
  const winners = outcomes.filter(Boolean);
  assert.equal(winners.length, 1);
  await assertBooking(winners[0], 'HELD');
  await assertOrders(id, winners);
  await cancel(winners[0]);
  const replacement = await reserve(id, customers[0], positions);
  await assertBooking(replacement, 'HELD'); await cancel(replacement);
}

try {
  await standingSale();
  await seatedSale();
  await mixedSales();
  await reservedSeats();
  for (const id of performances) await operator.post(`/api/operations/performances/${id}/cancel`);
  console.log(JSON.stringify({ run, status: 'passed', reports }));
} catch (error) {
  console.error(JSON.stringify({ run, status: 'failed', performances, reports, error: error.message }));
  // Retain the failed scenario for inspection; never delete history or conceal an uncertain write.
  process.exitCode = 1;
} finally {
  await Promise.allSettled([operator, ...customers].map(c => c.post('/app/logout')));
}
