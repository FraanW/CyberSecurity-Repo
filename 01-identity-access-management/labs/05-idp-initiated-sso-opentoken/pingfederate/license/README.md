# Put your licence file here

Save your PingFederate licence as **`pingfederate.lic`** in this folder.

- Git ignores `*.lic`, so it will not be committed. Keep it that way.
- It must be here **before** `docker compose up`, or Docker creates a folder with that name instead.
- A trial licence lasts 30 days. When it expires, generate a new one and replace the file.
