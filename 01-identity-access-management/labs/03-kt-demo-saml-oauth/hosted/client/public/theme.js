// Theme switcher — load in <head> so the saved theme applies before first paint.
// Two dark themes; the choice persists in localStorage ("kt-theme").
(function () {
  const THEMES = [
    { id: "maroon", label: "Velvet Maroon", bg: "#23080f", accent: "#ff7ab8" },
    { id: "ember",  label: "Ember Orange",  bg: "#200e03", accent: "#ff6600" },
  ];
  const KEY = "kt-theme";

  let saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) { /* storage may be blocked */ }
  document.documentElement.dataset.theme =
    THEMES.some((t) => t.id === saved) ? saved : THEMES[0].id;

  function select(id, dots) {
    document.documentElement.dataset.theme = id;
    try { localStorage.setItem(KEY, id); } catch (e) { /* ok to forget */ }
    dots.forEach((d) => d.setAttribute("aria-pressed", String(d.dataset.themeId === id)));
  }

  window.addEventListener("DOMContentLoaded", function () {
    const header = document.querySelector("header");
    if (!header) return;
    const wrap = document.createElement("div");
    wrap.className = "theme-dots";
    wrap.setAttribute("role", "group");
    wrap.setAttribute("aria-label", "Color theme");
    const dots = THEMES.map((t) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "theme-dot";
      b.dataset.themeId = t.id;
      b.title = t.label;
      b.setAttribute("aria-label", "Theme: " + t.label);
      b.setAttribute("aria-pressed", String(document.documentElement.dataset.theme === t.id));
      b.style.background = "linear-gradient(135deg, " + t.bg + " 55%, " + t.accent + " 55%)";
      b.addEventListener("click", () => select(t.id, dots));
      wrap.appendChild(b);
      return b;
    });
    header.appendChild(wrap);
  });
})();
