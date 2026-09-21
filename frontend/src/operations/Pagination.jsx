import React from "react";

export function Pagination({ offset, hasMore, onChange }) {
  return <div className="pagination">
    {offset > 0 && <button type="button" className="secondary" onClick={() => onChange(Math.max(0, offset - 20))}>Previous</button>}
    {hasMore && <button type="button" className="secondary" onClick={() => onChange(offset + 20)}>Next</button>}
  </div>;
}
