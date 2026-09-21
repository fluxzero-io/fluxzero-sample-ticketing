import React from "react";

export function Pagination({ offset, hasMore, onChange, size = 20 }) {
  return <div className="pagination">
    {offset > 0 && <button type="button" className="secondary" onClick={() => onChange(Math.max(0, offset - size))}>Previous</button>}
    {hasMore && <button type="button" className="secondary" onClick={() => onChange(offset + size)}>Next</button>}
  </div>;
}
