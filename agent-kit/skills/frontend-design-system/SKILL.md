---
name: frontend-design-system
description: The anti-AI-slop frontend design playbook. Use whenever you are about to design or build a UI — a landing page, app screen, component, or design system — and want it to look intentional and premium rather than generic. Covers design direction, tokens, typography, color, spacing, layout, depth, motion, and accessibility. Load BEFORE writing the first line of markup or picking any color.
---

# Frontend Design System — Anti-Slop Playbook

**TL;DR** — AI UIs look generic because "most probable" = the average of every template. Beat it by (1) committing to a *named direction*, (2) locking *design tokens* on paper, (3) doing typography + spacing + hierarchy in grayscale before color. Systems beat vibes.

---

## 0. Clarify first (never skip)
Design answers constraints. Get them before designing:
- **Direction/feel** + reference sites they like
- **Brand** (colors/fonts/logo) or greenfield
- **Audience, device, job-to-be-done**
- **Scope** (component / page / system) + **stack**
- **Constraints**: dark mode, a11y bar, perf budget, must-match existing UI

If they can't name a direction, offer 2–3 and let them choose. This one choice does the most anti-slop work.

## 1. Pick a direction, then lock tokens
Choose a real aesthetic and commit. Common directions:

| Direction | Feel | Type cue | Color cue |
|-----------|------|----------|-----------|
| Swiss / editorial | precise, confident, lots of whitespace | grotesk + serif display | mostly mono + one accent |
| Clean product (SaaS done right) | calm, trustworthy | one strong sans, real scale | neutral ramp + brand accent |
| Warm / organic | human, approachable | humanist sans / soft serif | earthy, warm neutrals |
| Brutalist | raw, bold, opinionated | oversized, tight tracking | high-contrast, few colors |
| Industrial / mono | technical, dense | monospace accents | grayscale + signal color |
| Luxe / premium | restrained, spacious | elegant serif | deep neutrals + metallic/jewel accent |

**Write the tokens down** in a `DESIGN.md` or token file — palette, fonts, radius, spacing, shadow, motion. Constraints on paper are what stop tools reaching for defaults.

## 2. Typography (the fastest escape from slop)
- **Kill default Inter/system-ui** unless deliberately chosen. Pick type with a point of view: a display face for headings + a clean workhorse for body.
- **Modular scale**, don't pick sizes ad hoc: `12 · 14 · 16 · 20 · 24 · 30 · 36 · 48 · 60`.
- **Body**: 16px+, line-height ~1.5, measure 60–75ch.
- **Hierarchy via weight + size + color**, not just size. Headings tighter line-height & tracking.
- Two families max. Load only the weights you use.

## 3. Color (add it LAST)
- **Design in grayscale first.** If hierarchy doesn't read in gray, color won't fix it.
- **Roles, not decoration**: `primary`, a full **neutral ramp** (50→900), **one accent**. Add semantic (success/warn/error) only as needed.
- Build ramps in **HSL/OKLCH**; keep hue consistent, vary lightness. Warm or cool your grays intentionally.
- **Contrast is law**: WCAG AA — 4.5:1 body text, 3:1 large text & UI components.
- Avoid the tell: the purple→blue gradient on gray. If you gradient, make it intentional and on-brand.

## 4. Spacing & rhythm (the luxury signal)
- **One spacing scale**: `4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 · 96`. Never eyeball one-off values.
- **Start generous.** The easiest upgrade to any design is more breathing room.
- Consistent gaps create rhythm; inconsistent gaps read as amateur.
- Relate spacing to type (line-height-aware vertical rhythm).

## 5. Layout (slop lives here, not in components)
- Break the **centered-hero + three-rounded-cards** template. Vary section rhythm; use asymmetry, an intentional grid, and alignment with purpose.
- Establish a grid (e.g. 12-col) and *use* it, including deliberate off-grid moments.
- Content-first: design around real copy and real data shapes, not lorem + placeholder boxes.
- Responsive by design — mobile layout is its own composition, not a squeezed desktop.

## 6. Depth & detail
- **Soft, layered shadows** for elevation; subtle 1px borders. One elevation system, applied consistently.
- Radius: pick a scale (e.g. 4/8/12/full) and stick to it. Don't round everything to the same blob.
- Icons: one set, consistent stroke weight. No mixed icon styles, no emoji-as-icons in serious UI.

## 7. Motion (purpose only)
- Micro-interactions that clarify or reward: hover, focus, press, state change, entrance.
- Ease-out, 150–250ms for UI; longer only for large/entrance moves.
- Respect `prefers-reduced-motion`. Never animate decoratively.

## 8. Accessibility (makes design better, not blander)
- Semantic HTML (`button` is a button), logical heading order, landmarks.
- Visible focus states — never `outline: none` without a replacement.
- Keyboard-operable everything; hit targets ≥44px.
- Alt text, form labels, `aria-*` only where semantics fall short.
- Test contrast and a keyboard-only pass before shipping.

## The 10-second slop check (run before shipping)
- [ ] Is the font a deliberate choice (not default Inter/system)?
- [ ] Is there ONE clear accent with real roles, or a random gradient?
- [ ] Does hierarchy read in grayscale?
- [ ] Consistent spacing scale, with generous breathing room?
- [ ] Did I break the centered-hero-three-cards template?
- [ ] Soft layered shadows, consistent radius — not heavy/uniform?
- [ ] Focus states + AA contrast + semantic HTML?
- [ ] Real content & states (empty/loading/error/long-text)?

**Next:** encode the chosen tokens in a `DESIGN.md` at project root so every future change inherits the direction instead of regressing to the mean.

---
*Grounded in Refactoring UI (Wathan/Schoger), design-token practice, Gestalt & Nielsen usability heuristics, and WCAG 2.2. Sources: [Refactoring UI](https://www.refactoringui.com/), [925studios — AI Slop Web Design guide](https://www.925studios.co/blog/ai-slop-web-design-guide), [WCAG 2.2](https://www.w3.org/TR/WCAG22/).*
