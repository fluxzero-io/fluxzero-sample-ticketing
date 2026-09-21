import { useEffect, useRef, useState } from "react";
import { api } from "../api";

// Read-only refresh keeps asynchronous payment and delivery results visible.
export function useLiveResource(url) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [revision, setRevision] = useState(0);
  const previousUrl = useRef(null);
  useEffect(() => {
    let active = true;
    let reading = false;
    if (previousUrl.current !== url) setData(null);
    previousUrl.current = url;
    setError(null);
    async function read() {
      if (reading || !url) return;
      reading = true;
      try {
        const value = await api(url);
        if (active) { setData(value); setError(null); }
      } catch (failure) {
        if (active) {
          setError(failure);
          if (failure.status === 401 || failure.status === 403) setData(null);
        }
      } finally { reading = false; }
    }
    read();
    const interval = setInterval(() => { if (!document.hidden) read(); }, 5000);
    return () => { active = false; clearInterval(interval); };
  }, [url, revision]);
  return { data, error, refresh: () => setRevision(value => value + 1) };
}

export function label(status) {
  return status?.toLowerCase().replaceAll("_", " ").replace(/^./, value => value.toUpperCase());
}
