import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";

const root = document.getElementById("root");

if (root) {
  // Deliberately not wrapped in StrictMode: its double-invoked effects would
  // register two visits for one link open in development.
  createRoot(root).render(<App />);
}
