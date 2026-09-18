import React, { useState, useEffect } from "react";
import { WarningCircle } from "@phosphor-icons/react";
import { signIn } from "./api";

export const Icon = ({ as: Component, ...props }) => (
  <Component size={22} weight="light" aria-hidden="true" {...props} />
);
export function ErrorMessage({ error, retry }) {
  return error ? (
    <div className="notice error" role="alert">
      <Icon as={WarningCircle} />
      <span>{error.message}</span>
      {error.status === 401 ? (
        <button onClick={signIn}>Sign in</button>
      ) : (
        retry && <button onClick={retry}>Try again</button>
      )}
    </div>
  ) : null;
}
export function Spinner({ children = "Loading…" }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      {children}
    </div>
  );
}
export function useRemote(load, dependencies) {
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setData(null);
    setError(null);
    load()
      .then((v) => active && setData(v))
      .catch((e) => active && setError(e));
    return () => {
      active = false;
    };
  }, [...dependencies, version]);
  return [data, error, () => setVersion((v) => v + 1), setData];
}
