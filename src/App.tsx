import { Marketing } from "./Marketing";
import { CaptureFlow } from "./capture/CaptureFlow";

/** /c/{token} (with host fallback) or ?t={token}; both are accepted. */
function tokenFromUrl(): string {
  const query = /[?&]t=([^&#]+)/.exec(window.location.search);
  if (query) return decodeURIComponent(query[1]);

  const path = /\/c\/([^/?#]+)/.exec(window.location.pathname);
  return path ? decodeURIComponent(path[1]) : "";
}

export default function App() {
  const token = tokenFromUrl();

  return (
    <div className="wrap">
      {token ? (
        <>
          <div className="brand">
            <div className="brand-mark">C</div>
            <div className="brand-name">CyberTrace</div>
          </div>
          <CaptureFlow token={token} />
        </>
      ) : (
        <Marketing />
      )}
    </div>
  );
}
