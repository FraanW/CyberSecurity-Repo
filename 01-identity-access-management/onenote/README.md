# OneNote-pastable exports

> **What this is.** Browser-ready HTML copies of the SAML notes, tuned so they paste into **OneNote** with all formatting intact — headings, tables, code blocks, colours, bold. Markdown pasted straight into OneNote looks like raw `#` and `|` symbols; these files fix that.

## Why HTML (not the `.md`)
OneNote's paste captures the **rendered** look from a browser, not the source. So the trick is: render the note as clean, light-theme HTML first, then copy *that*. These files also expand every collapsible answer (OneNote can't collapse), so nothing is hidden.

## How to use (30 seconds)
1. **Open** the `.html` file in a browser (double-click it).
2. Press **`Ctrl` + `A`** (select all), then **`Ctrl` + `C`** (copy).
3. In OneNote, click into a page and press **`Ctrl` + `V`** (paste).
4. Delete the little blue instruction banner at the top — it's just a reminder.

✅ **Checkpoint:** headings, the tables, and the XML code blocks should all keep their styling in OneNote. If a table looks plain, make sure you copied from the **rendered browser page**, not the raw file.

## Files
| File | Source note | What's inside |
|---|---|---|
| `13-saml-mastery-session2.onenote.html` | [`../notes/13-saml-mastery-session2.md`](../notes/13-saml-mastery-session2.md) | The full SP-init/IdP-init + assertion + certificates + encryption deep dive |
| `14-saml-question-bank.onenote.html` | [`../notes/14-saml-question-bank.md`](../notes/14-saml-question-bank.md) | The tiered question bank, every answer shown expanded |

> **Tip:** each file → its own OneNote page. Regenerate these whenever the source notes change (ask Janus).

*— Janus 🔐*
