# Reverse KT — Presentation Flow Script (spoken)

> This is **not** a slide doc. It's the thing you read the night before and glance at between slides — the *spoken* version of the talk, in your own voice, so you sound like you're explaining it to a friend, not reading a Wikipedia page.
>
> How to use it:
> - The **left margin** tells you where you are (which slide / which lab screen).
> - The **plain paragraphs** are roughly what you'd *say* — not to memorise word for word, just to catch the rhythm and the phrasing. Say it your way.
> - **[brackets]** are stage directions — what to click, what to point at, when to pause.
> - Lines marked **→ check-in** are the moments you stop and look at the room. Don't skip these; they're what makes it a conversation instead of a lecture.
>
> Rule of thumb: if a sentence feels like it belongs in a textbook, cut it in half and say the human version.

---

## Before you start (30 seconds, no slide)

Water within reach. Laptop already on the demo, both tabs open (client app + Keycloak), SAML-tracer pinned, Network tab open on a *second* window so you're not fumbling. Phone on silent. Take one breath. You know this material better than you think — you've been living in it.

Then start.

---

## 0 · Opening — the room

**[Title slide up]**

Good morning, everyone. Thanks for making the time.

Before I start — Gugan, Kannan, Lalit, thank you. Most of what I'm about to say, I learned because one of you took ten minutes to explain it when I was stuck. So this is a bit of a role-reversal today, and if I get something wrong, please jump in — I'd rather be corrected in the room than sound confident and wrong.

And Shankar — thank you for sitting in. I know your calendar, so it genuinely means a lot.

So here's the deal for the next [X] minutes. I'm going to walk through what cybersecurity actually is, then narrow down to our world — IAM — and by the end I'll show you a live demo where you can literally *watch* the login handshakes happen in the browser. SAML, OAuth, the whole thing, on screen. No slides for that part — real traffic.

→ check-in: *"Sound good? Stop me anytime — questions are better in the moment than saved for the end."*

---

## 1 · Why any of this matters

**[Slide: a couple of recent headlines]**

Let me start with why we all have jobs.

A few years ago, if you wanted to break into a company, you attacked the network — the firewall, the perimeter, the walls. So security was basically about building higher walls.

That's over. Nobody "breaks in" through the wall anymore. They **log in**. They get a username and a password, or they trick someone into approving a login, and they walk through the front door looking exactly like a real employee.

**[Point at the headlines]**

Look at these. The big casino breaches a couple of years back — MGM, Caesars — that wasn't some genius zero-day. Someone called the IT help desk, pretended to be an employee, and got a password reset. That's it. A phone call took down a casino for days.

Uber, similar story — an attacker just kept spamming an employee with MFA prompts at 2am until the poor guy tapped "approve" to make it stop. We even have a name for it now: MFA fatigue.

→ check-in: *"Notice the pattern? None of these are about the network. They're all about **identity** — someone becoming someone they're not."*

And that's the one line I want you to walk out with today: **the attacker's favourite tool isn't a virus. It's a valid login.**

---

## 2 · Where we sit — FinCo

**[Slide: FinCo scale]**

Now bring that home. We're a fintech. We're not guarding a blog — we're guarding money and people's financial lives. We've got something like 10,000+ products and services running, thousands of employees, partners, APIs, systems talking to other systems all day long.

Every single one of those is a door. And every door needs to know two things: *who are you*, and *are you allowed in here*.

That's the whole job. Multiply it by ten thousand.

→ check-in: *"So when people ask me what I do — I don't say 'security'. I say: I'm one of the people who decides who gets through which door. At a bank. That usually lands."*

---

## 3 · The map — cybersecurity has many rooms

**[Slide: domains grid]**

Quick zoom-out so you see where IAM fits, because "cybersecurity" is a huge word and it hides a lot.

Think of it like a hospital. Nobody says "I work in medicine" and stops there — there's cardiology, radiology, surgery, and so on. Security's the same. You've got —

**[Point across the grid, keep it light, don't read every one]**

- Network security — the walls and gates.
- Application security — making sure the apps themselves aren't full of holes.
- The blue team, the SOC — the people watching the cameras, hunting for intruders.
- The red team — the ethical hackers we *pay* to break in and tell us how.
- GRC — governance, risk, compliance — the folks who make sure we can prove to auditors and to RBI that we're doing all this properly. Big deal in fintech.
- Threat intel, data security, cloud security... the list goes on.

And then there's **us. IAM.** Identity and Access Management.

→ check-in: *"And here's my slightly biased take — everything else on this slide assumes IAM already worked. The firewall, the SOC, all of it — it all rests on the system correctly knowing who you are first. Get identity wrong and nothing else matters."*

---

## 4 · IAM — what, why, how

**[Slide: IAM definition — keep it one line]**

So — IAM. Forget the textbook definition for a second.

**What** it is: making sure the **right people** get the **right access** to the **right things**, for the **right reasons**, and — this is the part people forget — **only for as long as they need it.**

**Why** it exists: because "just give everyone access to everything" is how you end up on the news. And "make everything so locked down nobody can work" is how you get fired for slowing the business to a crawl. IAM is the balance in the middle.

**How** we do it: that's the rest of this talk. The protocols, the directories, the tools. But hold onto the *what* and the *why*, because every technical thing I show you today is just a clever way of answering those same two questions — *who are you*, and *what are you allowed to do*.

---

## 5 · Identity is the new perimeter

**[Slide: old wall vs. the badge]**

Remember what I said — nobody breaks the wall, they log in. Let me put a name on that idea, because the industry has one.

For decades, the "perimeter" — the thing you defended — was the network boundary. Inside the office = trusted. Outside = not.

But now? People work from home. Half our stuff is in the cloud. Apps talk to other companies' apps. There is no "inside" anymore. The wall has holes in it by design.

So the perimeter moved. It's not the network edge now — **it's identity.** Your identity is the wall. Every login is a border checkpoint. That's the shift, and it's why our little team punches above its weight.

→ check-in: *"'Identity is the new perimeter' — if you only remember one buzzword from today, make it that one. It's on every vendor's slide for a reason: it's actually true."*

---

## 6 · The two words: Authentication vs Authorization

**[Slide: AuthN vs AuthZ]**

Okay, the two words you'll hear me say all day. They sound the same, people mix them up constantly, and they are *not* the same thing.

Let me use something we all touch every single morning — the **office access card.**

**Authentication** — AuthN — is the moment you tap your card at the main gate and the light goes green. The building just confirmed *you are Farhaan.* That's it. That's authentication. **Proving who you are.**

**Authorization** — AuthZ — is what happens *next.* You walk over to the server room, tap the same card, and the light goes **red.** You're still you — authentication was fine — but you're **not allowed in that room.** That's authorization. **What you're permitted to do once we know who you are.**

→ check-in: *"Same card. Same person. Green at the front door, red at the server room. Authentication got you into the building. Authorization decides which rooms open. Everything today is some version of these two lights."*

Hold that access-card picture in your head — I'm going to keep coming back to it.

---

## 7 · The pieces of IAM

**[Slide: IAM building blocks]**

IAM isn't one thing, it's a few moving parts working together. Let me go through them with the same building analogy, and I'll name the actual tools we use so it's not all theory.

**[Take these one at a time — don't rush]**

**Identity Lifecycle — "Joiner, Mover, Leaver."**
Think of the HR journey. Someone **joins** — day one, they need an account, an email, access to the right systems, and not a minute later, because they're sitting there unable to work. Then they **move** — Priya was in Support, now she's in Payments, so her old access should drop and new access appear. And finally they **leave** — and this is the one that bites companies. The day someone quits, every door has to close. That "still-active account of someone who left six months ago"? That's a gift to an attacker.

The tool that automates all of that for us is **SailPoint.** It's the system that says "new joiner in Payments team → give them exactly these accesses, automatically" and, just as importantly, "this person left → rip it all out." Nobody doing it by hand on a spreadsheet.

**Authentication** — we just did this. Proving who you are. Passwords, MFA, biometrics, the works.

**Authorization** — also did this. What you can touch once you're in. Roles, permissions.

**PAM — Privileged Access Management.**
Some keys aren't like the others. The admin account, the root password, the database master key — the keys to the kingdom. You don't leave those in everyone's pocket. PAM is the locked key-cabinet: the powerful credentials live in a vault, you have to check them out, someone's watching, and the session gets recorded. Think of the master key to every room in the building — you don't hand that out, you keep it in a glass case and log every time it's used.

**IGA — Identity Governance and Administration.**
This is the audit-and-questions layer. *Who has access to what? Should they still? Who approved it? Prove it.* Every quarter, managers get a list — "here's everyone with access to this system, tick the ones who should keep it." That's an access review, and IGA runs it. In a bank this isn't optional — the auditors and the regulator will ask, and "trust me" is not an answer. **SailPoint** does this for us too — same platform, the governance brain of the whole operation.

**Directory Services.**
And under all of it, somebody has to actually *store* all these identities — the usernames, the groups, who's in which team. That's the directory. The big phone book of the whole company. The classic technology here is **LDAP**, and the product most enterprises run is **Active Directory** — AD — from Microsoft. When you log into your Windows machine in the morning, you're talking to Active Directory. It's the source of truth for "who exists here."

→ check-in: *"So — SailPoint handles the joining, moving, leaving and the governance. Active Directory is the phone book underneath. And PAM guards the crown-jewel keys. That's the machine room of IAM. None of it's glamorous, all of it matters."*

---

## 8 · How did we even get here? Passwords → RADIUS → federation

**[Slide: evolution timeline]**

Quick bit of history, because it explains why the fancy stuff exists.

In the beginning there was just the **password.** Every app had its own. You had fifty apps, you had fifty passwords, and — be honest — they were all the same password with a "1" on the end. Terrible, but that's where we started.

Then, as companies grew, they thought — okay, we can't have every system checking passwords its own way. Let's have **one** central place that checks credentials, and every device just *asks* it. One of the early answers to that was **RADIUS** — think of your office Wi-Fi or the VPN. When you connect, the Wi-Fi doesn't know your password; it forwards your login to a central server that does, and waits for a yes or no. That was a big idea: **stop making every app store passwords; centralise the checking.**

And that idea — "don't handle the password yourself, ask a trusted central authority" — is the seed of *everything* modern. SAML, OAuth, all of it, is that same instinct grown up. Which brings us to the thing everybody actually wants: logging in *once.*

---

## 9 · SSO — logging in once

**[Slide: SSO — one login, many apps]**

Here's something you've all done and never thought about.

You come in, you log into the **FUEL** intranet in the morning. And then you click through to SharePoint, and... it just opens. You didn't log in again. You click another internal tool — opens. No password. You logged in *once* and a dozen apps just quietly let you in.

That's **Single Sign-On.** SSO. One front-door login, and then a bunch of apps trust that you're already vouched for.

→ check-in: *"And notice — you never think 'oh nice, SSO.' You only ever notice it when it's* broken *and something asks you to log in* again. *That's the mark of good IAM: when it works, it's invisible.*"

So the obvious question is — *how?* How does SharePoint know you already logged into FUEL and just wave you through? SharePoint never saw your password. So how does it trust you?

That question — *how does one app trust that another system already checked you* — is exactly what SAML answers. Let's go there.

---

## 10 · SAML — the notarised letter of introduction

> You told me you've got the SAML content itself — the actors, the flow, AuthnRequest/Response, the signature. So this section is **pointers, not a script.** Hit these beats in your own words, in this order, and you're golden.

**[Slide: SAML three actors]**

**The one-line hook to open with:** *"SAML is how one system vouches for you to another — like a notarised letter of introduction."*

**The analogy to plant first (before any XML):**
You want into an exclusive club (the app). You don't know the bouncer. But your bank manager, whom the club *does* trust, writes you a sealed, notarised letter: "This is Farhaan, I've verified him, let him in." You hand it over. The bouncer checks the seal is real, and in you go. The bouncer never saw your ID — he trusted the seal. **That letter is the SAML assertion. The notary's seal is the digital signature.**

**Then name the three actors** (point at the slide):
1. **You** — the Principal / the user in the browser.
2. **The IdP — Identity Provider** — the one who knows you and vouches. *"This is our FUEL login, or PingFederate, or Entra."*
3. **The SP — Service Provider** — the app you're trying to get into (SharePoint, Salesforce, whatever).

**Then walk the flow** (this is the bit you've got — just keep it to the story):
- You hit the app (SP). It doesn't know you.
- The app writes an **AuthnRequest** — "hey IdP, I don't know this person, can you check them?" — and bounces your browser over to the IdP.
- The IdP checks you (password, MFA — or, if you're already logged into FUEL, it *doesn't even ask again* — **there's your SSO**).
- The IdP writes the **SAML Response**, and inside it the **assertion** — the letter — and it **signs it** with its private key.
- Your browser carries that signed assertion back to the app.
- The app checks the signature against the IdP's **certificate** — the public half. Seal's genuine? You're in.

**The two points to land hard:**
- **The app never saw your password.** It only ever saw a signed statement from someone it trusts. *That's* the magic — the app outsources the hard part.
- **The signature is the whole ballgame.** Trust the assertion, not the password — but only *because* the signature proves the IdP really wrote it and nobody tampered with it in your browser. If anyone could forge that assertion, the whole thing collapses. (And that's a real attack — it's called Golden SAML, it's how the SolarWinds attackers moved around. Worth one sentence if the room's sharp.)

**Close SAML with:** *"So that's federation. One trusted authority vouches, everyone else relies on the vouching. We'll watch a real one of these fly across the wire in the demo — you'll see the actual signed XML."*

→ check-in before moving on: *"With me so far? Because SAML was built for this — a person, in a browser, logging into an app. Now let me show you the problem SAML* doesn't *solve, and that's where OAuth comes in."*

---

## 11 · OAuth — the valet key

**[Slide: OAuth — delegation]**

Okay. SAML was about *logging you in.* OAuth answers a completely different question, and this trips people up, so let me be crisp about it.

OAuth isn't really about logging in. **OAuth is about giving one app permission to do something on your behalf in another app — without handing over your password.**

Here's the picture. You've got a fancy car, and it comes with two keys. The full key opens everything — boot, glovebox, drives anywhere. And a **valet key** — it *only* lets the parking guy drive it 50 metres and park it. Can't open the boot, can't go far.

When you use "Log in with Google" on some app, or when you let a budgeting app read your bank transactions — you are handing that app a **valet key.** Limited. "You may read my transactions. You may *not* move money. And I can take this key back whenever I want." You never gave them your actual banking password. You gave them a valet key.

→ check-in: *"In India this is literally regulated now — it's called Account Aggregator. A fintech app can see your bank statements only because you handed it a valet key through your bank, and you can revoke it. That's OAuth, running the country's financial plumbing."*

**[Slide: OAuth actors + the three tokens]**

The cast:
- **You** — the Resource Owner. It's your stuff.
- **The app** wanting access — the Client.
- **The Authorization Server** — the one who issues the keys. (For us, that's Keycloak in the demo; PingFederate or Entra in real life.)
- **The Resource Server** — the thing being protected, the API holding your data.

And then, the **three tokens** — think of them as three different pieces of paper:
- **Access token** — the valet key itself. Short-lived on purpose. It's what the app shows the API to get in. Expires fast — usually minutes — so if it leaks, it's a key that stops working almost immediately.
- **Refresh token** — the "give me a fresh valet key" coupon. When the access token expires, the app quietly swaps this for a new one, so you're not logging in every five minutes. Longer-lived, kept more carefully.
- **ID token** — hold that one. It belongs to OIDC, and I'll get to it in a minute — it's the piece that turned OAuth into a login system.

→ check-in: *"Access token = the valet key, expires fast. Refresh token = the coupon for a new valet key. That's the pair that makes the whole thing both secure* and *not annoying."*

---

## 12 · Grant types — same idea, different recipes

**[Slide: grant types]**

Now — "grant type" is a scary phrase for a simple idea. A grant type is just **the recipe for how the app gets that valet key**, and the recipe changes depending on *who's involved* and *what kind of app it is.* Different situation, different recipe. That's all.

Let me give you the four that actually matter, each with a one-liner:

- **Authorization Code (with PKCE)** — the gold standard. A real person, in a browser or a phone app, logging into something. This is 95% of what you'll see, and it's the one I'll demo first. *(PKCE — I'll explain in a second, it's a lovely little trick.)*

- **Client Credentials** — **no human at all.** One server talking to another server at 3am. A batch job pulling data from an API. There's no user to ask, so the app just proves *it's itself* with a secret and gets a token. Machine-to-machine.

- **Device Flow** — for things with no keyboard. Your smart TV. You want to log into Netflix on the TV, and typing your password with the remote is torture. So the TV shows a code and says "go to netflix.com/activate on your phone and type this in." You authorise on the comfy device, the TV gets its token. That's the device grant.

- **Refresh Token** — technically a grant too: "here's my coupon, give me a fresh access token." The silent renewal in the background.

And then two I'll mention only to tell you **not** to use them:
- **Implicit** — old, deprecated, threw tokens around too loosely. Dead.
- **ROPC / Password grant** — where the app collects your actual password and trades it for a token. Which is *exactly* the thing OAuth was invented to avoid. It exists, it's deprecated, and I'll show it in the demo purely so you can see why it's the bad one.

→ check-in: *"So — same valet key idea every time. The only thing that changes is the recipe for handing it over, and the recipe depends on whether there's a human, and what device they're on. That's the entire concept of grant types."*

---

## 13 · PKCE — the coffee shop

**[Slide: PKCE — keep it simple, maybe just a coffee cup]**

Let me do PKCE properly because it's my favourite little bit of security design, and it maps perfectly to something we all do.

You walk into a busy coffee shop. You order a coffee, you pay, and — here's the thing — you walk away to go find a seat. You're not standing at the counter. A minute later the barista calls out "one cappuccino!"

Now — how does the barista make sure *you* get the coffee and not the guy standing closer who just goes "yeah that's mine"? At a good place, they check the name on the cup against your receipt. **The person collecting the coffee has to prove they're the same person who placed the order.**

That's PKCE. Exactly that.

Here's the problem it solves. In that Authorization Code flow, the app first gets a temporary ticket — the "authorization code" — and then swaps that ticket for the real token. But there's a gap in between, and on a phone especially, a sneaky app could grab that ticket mid-air and try to redeem it — "yeah, that coffee's mine."

So PKCE does the coffee-shop trick. At the very start, when the app places the order, it whispers a secret — well, it shows a scrambled version of a secret, a fingerprint of it. Then at the end, when it comes to collect the token, it has to show the *original* secret. The auth server checks: does this match the fingerprint from the start? Same customer? Great, here's your token. Doesn't match? *Someone stole the ticket* — denied.

→ check-in: *"So PKCE is just: prove that the one collecting the token is the same one who started the login. Name on the cup matches the receipt. And you'll* see *this in the demo — there's a scrambled value going out at the start, and the matching secret coming back at the end. It's right there in the browser."*

---

## 14 · OIDC — the missing ID card

**[Slide: OIDC = OAuth + identity]**

Last concept, then we go live. And this one closes a loop.

I said OAuth is about *permission*, not *login* — the valet key, remember. It's great at "this app may read your transactions." But it has a blind spot: OAuth, on its own, **doesn't actually tell the app who you are.** The valet key proves the app is *allowed* to do something; it doesn't come with your photo ID.

So the industry bolted a thin, standard layer on top of OAuth to fix exactly that. That's **OIDC — OpenID Connect.** One line: **OIDC is OAuth plus an ID card.**

And that ID card is the **third token** I made you hold earlier — the **ID token.** It's a little signed card that says "this is Farhaan, here's his email, here's when he logged in, and yes, the IdP verified him." Now the app doesn't just have permission — it *knows who you are.*

**[Point back at everyone]**

And here's the payoff — every time you've clicked **"Sign in with Google"** or "Sign in with Microsoft," *that's this.* That's OIDC. OAuth gets the valet key, and the ID token on top says "...and by the way, this is definitely Farhaan." That one addition is what turned OAuth from a permission system into the login button that runs half the internet.

→ check-in: *"So to tie the bow on it — SAML and OIDC are actually cousins. Both are about federated login, one trusted party vouching for you. SAML's the older, XML, enterprise one — think SharePoint, Salesforce. OIDC is the newer, lighter, JSON one that OAuth made possible — think 'Sign in with Google,' mobile apps, modern stuff. Same instinct, two generations."*

---

## 15 · The live lab — let's actually watch it happen

**[Switch to the browser. Slides off. This is the part they'll remember.]**

Right — enough talking. Everything I just described, you can *see.* I'm going to open the browser's own tools and we're going to watch these handshakes fly across the wire in real time. No magic, no hand-waving — the actual traffic.

Two tools I'm using:
- The browser's **Network tab** — records every request the browser makes. That's how we'll watch OAuth.
- A little add-on called **SAML-tracer** — same idea but it decodes the SAML XML nicely for us. That's for the SAML demo.

**[Small honesty note if the site's been idle:]** *heads up, this is on a free hosting tier, so the very first click might take 30-odd seconds to wake the server up. Bear with it — after that it's quick.*

### 15a · SAML, live

**[Open SAML-tracer, then the SAML page of the app]**

Watch this. I'm going to log into this app using SAML. I've armed SAML-tracer so it catches everything.

**[Click "Start at the App" — SP-initiated. Log in as farhaan / Passw0rd!]**

→ Point at SAML-tracer, walk it slowly:
- *"See this first entry? That's the **AuthnRequest** — that's our app saying to the IdP, 'I don't know this person, check them for me.' Remember the club and the letter — this is the app asking the notary."*
- *"I log in... and here — this second entry — this is the **SAML Response.** And look —"* **[expand the XML]** *"— that big block is the **assertion.** The letter. It's got my name in it. And see this **Signature** section? That's the notary's seal. Our app checked that seal against the IdP's certificate before it let me in. It never saw my password — just this signed letter."*
- One more pointer: *"See `InResponseTo` here? That ties this response back to the exact request that started it. That's the app going 'yes, this is the answer to the question I actually asked' — not some random letter someone slipped under the door."*

**[Now show SSO — click to access the app again, or a second SAML app if wired up]**

*"And now watch — I go back to the app... straight in. No password. Because the IdP still remembers me. That — that right there — is the SharePoint-after-FUEL thing from earlier. That's SSO, and you just watched it happen."*

**[If doing logout:]** *"And when I hit full logout — see this **LogoutRequest** going to the IdP? The app's telling the IdP 'end her session everywhere.' Single Log-Out. Now if I try again... it asks for the password. Proving the session was the thing carrying me through."*

### 15b · OAuth + PKCE, live

**[Switch to Network tab, open the OAuth / Authorization-Code page]**

Now the OAuth side. I'll do the gold-standard flow — Authorization Code with PKCE, the coffee shop one.

**[Clear the Network tab. Click login.]**

→ Walk it:
- *"First request — going to the `/authorize` endpoint. And look in the parameters —"* **[point at `code_challenge`]** *"— see this `code_challenge`? That's the **scrambled fingerprint** of the secret. That's me telling the barista my name as I order."*
- **[Log in, get bounced back]** *"Now it comes back with a `code` — that's the temporary ticket, the receipt."*
- **[Point at the token request]** *"And here's the swap — the app trades the ticket for the real token. And see `code_verifier` in this one? That's the **original secret.** The auth server just checked it against the fingerprint from the start. Name on the cup matches the receipt. Match — token granted."*
- **[Show the token / decode it if you've got the viewer]** *"And there's the access token — the valet key. If there's an **ID token** next to it, that's OIDC — that's the 'and this is definitely Farhaan' card."*

**[If time — one or two more grants, quickly:]**
- *"Quick one — **client credentials.** No login screen at all, watch —"* **[run it]** *"— just a server proving it's itself and getting a token. That's the 3am batch job. No human."*
- *"And the **device flow** — see this? It gives me a code and a URL."* **[show the QR]** *"That's your smart TV. I scan this on my phone, approve there, and the 'TV' gets its token. No typing passwords with a remote."*
- *(Optional, if the room's engaged:)* *"And just so you see the villain — here's the old **password grant.** The app literally takes my password and sends it on. Works fine. And that's the problem — the app* saw *my password, and my MFA got skipped. That's the anti-pattern everything else exists to kill. Deprecated for good reason."*

### 15c · Land the plane

**[Close the laptop lid halfway, turn back to the room]**

So — that's the whole story, and you just watched the real thing, not slides.

Someone logs in *once.* A trusted authority — an IdP — vouches for them with a signed letter, and every app relies on that instead of hoarding passwords. That's SAML, and that's SSO. When an app needs to act *on your behalf* somewhere else, it gets a limited, revocable valet key instead of your password — that's OAuth, in different recipes for different situations. And when the app also needs to *know who you are*, OIDC hands it a signed ID card on top. Underneath it all, SailPoint's managing who *should* have what, Active Directory's the phone book, and PAM's guarding the crown jewels.

And every bit of it is answering the same two questions we started with — *who are you*, and *what are you allowed to do.* Green light at the front door, red light at the server room. That's IAM. That's the job.

→ Final line: *"That's what I do at FinCo — and honestly, after building this demo, I understand it about ten times better than when I started, which is the whole point of a KT, right? Thank you — Gugan, Kannan, Lalit, Shankar, all of you. Questions — throw them at me."*

---

## Appendix · If someone asks a hard one

Quick reflexes so you're not caught flat. Keep answers short; offer to go deeper after.

- **"What's the difference between SAML and OIDC — when do you use which?"**
  *"Same job — federated login. SAML's the older enterprise one, XML, heavy — SharePoint, Salesforce, big B2B. OIDC's the modern lightweight one built on OAuth, JSON — mobile apps, 'Sign in with Google,' anything new. If I'm building fresh today, OIDC. If I'm integrating a 15-year-old enterprise app, probably SAML."*

- **"Is OAuth authentication or authorization?"**
  *"Authorization — permission. Pure OAuth doesn't tell the app who you are. The moment you need 'who is this,' you've added OIDC on top. People misuse plain OAuth as login and that's actually a classic security mistake."*

- **"Where does MFA fit in all this?"**
  *"MFA lives at the IdP, during authentication — it's part of how the IdP convinces itself you're you, before it ever writes the assertion or issues a token. So it happens once, at the front door, and every downstream app benefits without implementing it themselves. That's another quiet win of federation."*

- **"What's the actual risk if a token leaks?"**
  *"Depends which. Access token — short-lived valet key, leaks are painful but self-expiring in minutes. Refresh token — worse, longer-lived, which is why it's guarded harder and rotated. A SAML assertion or the IdP's signing key — that's the crown jewels; forge those and you're 'Golden SAML,' you can impersonate anyone. Which is exactly why PAM exists to lock those keys away."*

- **"Why Keycloak in the demo and not PingFederate?"**
  *"Same standards, Keycloak's just free and open-source so I could host it. Everything you saw — the SAML assertion, the OAuth grants, PKCE — is identical protocol-wise to what PingFederate or Entra do. The concepts transfer one-to-one; only the admin console looks different."*

- **If you genuinely don't know:**
  *"Good question — I don't want to guess and mislead you. Let me find out and get back to you."* (Gugan/Kannan will respect that a hundred times more than a bluff.)

---

*Written for the reverse KT. Everything demoed is a lab — Keycloak + a sample app, no real FinCo systems, no real credentials on screen. Speak slowly, breathe, and let the demo do the heavy lifting. You've got this.*
