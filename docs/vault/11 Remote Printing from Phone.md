# Remote Printing from Phone (Bills + Labels)

## Status
Draft / exploration only — not yet implemented.

## Problem statement
In the shop there are times when only the owner's father is present, and he is
not comfortable using the computer. He still needs to:
- print a bill/receipt from an existing template; and
- print barcode labels whose values change every time (e.g. item codes, serials,
  QR payloads).

The shop has a PC on the network that already runs InvoiceStudio. A phone is the
device the non-technical person will actually hold.

So the question is: how can a phone trigger those prints without making the person
operate the PC, while allowing only the required data to go over the internet when
needed, with that internet traffic encrypted and keys known only to the devices
involved (after an initial setup you perform)?

## Constraints (in priority order)
1. **Only the required data may go over the internet, and only when encrypted.**
   The core print flow is still local-first; any internet traffic should carry only
   what is strictly necessary for the remote-control path, not a full copy of the
   invoice or label data unless the shop explicitly chooses that.
2. **Encryption model is device-held.** Keys/key material are known only to the
   participating devices (phone + shop PC) after an initial setup you perform once.
   The cloud relay should not be able to read the payloads it passes.
3. **Phone should not need network skill.** "Pick what to print, tap print".
4. **Reuses the existing app.** Templates and `TemplateDao` data stay the source of
   truth; no parallel template system.
5. **Covers bills/receipts (from templates) and labels (dynamic values).**
6. **Ship later on Android** without forcing the whole thing to be cloud-native.

## Architecture options considered

### A. Encrypted remote control over the internet (phone and PC on different networks)

- The phone and shop PC both reach a cloud relay over the internet, but the relay
  passes **encrypted payloads** between them. Only the phone and the shop PC can
  decrypt the print job data.
- The internet is used for what is actually needed: the job request and whatever
  control/metadata the remote path truly requires. The shop can still decide that
  some jobs only ever travel locally.
- This is the model that fits "only required data over the internet, encrypted, keys
  only on our devices". It is different from a thin signal-only layer because the
  encrypted payload can now include the actual job data the PC needs, while still
  remaining opaque to the cloud.

### B. LAN service on the shop PC (same network)

- A small HTTP/WebSocket service on the shop PC is reachable from the phone on the
  shop LAN.
- The phone lists templates/printers and requests a bill or label print.
- Good for in-shop use when the phone is already on the LAN. Does not help when the
  phone is elsewhere.

### C. Phone talks directly to the printer over the LAN

- Some network printers accept jobs directly.
- **Why not the main path:** the existing workflow is template + layout + runtime
  data on the PC. That more directly reuses templates and avoids redoing layout
  work per printer protocol. Keep this as an optional output lane later.

### D. Local-first with an encrypted remote-control layer on top

- Start with a LAN service/screen the shop PC exposes.
- When phone and PC are not on the same LAN, layer an encrypted remote-control path
  on top, still with only required data on the wire.
- Thin phone app, data local, easier to extend later to the non-LAN path if needed.

## Firebase's possible role (and where it is not needed)

Firebase is one possible cloud platform, but the design question here is really: if
a cloud relay is used, how do we make it carry only encrypted, required data between
the devices?

### Where Firebase and similar backends could help

- **A relay between phone and shop PC when they are on different networks.** If you
  choose the encrypted remote-control model, the cloud can pass encrypted job
  messages and delivery/ack signals between the devices.
- **Phone app non-data concerns**: updates, crash reporting, analytics, remote config
  — none of which involve invoice or barcode data.

### What changed versus the earlier signal-only version

- In the earlier read of this doc, the cloud was only a tiny control signal. With the
  new constraint, the cloud can carry **required job data too**, as long as it is
  encrypted and the cloud itself cannot read it.
- That means Firebase is no longer just a signal layer in principle — it can be the
  transport for the remote-control jobs, provided the payload is encrypted before it
  leaves the phone and is only decipherable on the shop PC.

### Where Firebase still does not fit well

- If you want the cloud to *process* or *store* the invoice/barcode contents in the
  clear, that violates the shop's stated constraint, even if it is encrypted in
  transit for a moment.
- If you do not want any required data on the internet at all, then the remote path
  is not acceptable and LAN-only is the honest model.

### Honest trade-off statement

- **Same LAN:** LAN service is simpler and has no internet involvement in the print
  path.
- **Different networks, with only required data allowed over the internet:** encrypted
  remote control is the design to evaluate. Firebase is plausible as the relay/store
  for that path, but it is a transport and signaling role, not a data-processing role.
- **The deciding question is not Firebase vs no Firebase, but which data is allowed to
  be on the internet at all, and whether device-held encryption is acceptable for the
  remote path.**

## Best solution shape (recommended direction)

### 1. Keep the real work local by default

The shop PC still holds templates, the template engine, the printer, and the
database. The phone is the friendly trigger surface, not a remake of the print
pipeline.

### 2. Build an encrypted remote-control path as the remote option, not as the default

- When phone and PC are not on the same LAN, the phone sends only what the remote
  control path truly needs.
- That path is encrypted end-to-end between the phone and the shop PC, using keys
  set up once on those devices.
- The cloud relay should be able to deliver the message but not read the contents.

### 3. Phone app is the low-skill UI

- pick a template or label kind;
- for bills, choose the data (recent sale, quick manual entry, lookup) and print;
- for labels, enter/reuse the changing value and print;
- big, simple buttons a non-technical person can use.

### 4. Barcode values are captured on the phone, rendered/printed on the PC

The hard part for labels is gathering the changing value. That is where the user
is — on the phone. The PC does the actual label rendering and printing using the
existing template. This split keeps the templates and printers on the shop side and
lets the phone focus on value capture.

### 5. Least-privilege data over the internet, by default

- For remote bills, ask whether the job really needs the full invoice data on the
  wire, or only enough for the PC to reproduce the print from its local state.
- For remote labels, the changing value is the natural required payload; the template
  can stay local.
- The smaller the remote payload, the less you have to defend, and the easier the
  key-management story.

## What gets rendered where (the key split)

For bills, the split is simple because the template already exists and can live on
the shop PC:

- phone: choose template + the runtime data needed for this print;
- shop PC: render the bill from the template and print.

For labels, the split matters more because the value changes every time:

- phone: capture the value (type, scan, choose) and the label kind;
- shop PC: render the label from the template using that value, then print.

Net effect: the remote path carries only the data the PC actually needs for the job,
and when you stay on the same LAN, the internet is not involved for the print at all.

## Practical options to decide between

1. **In-shop only, LAN service.** Simplest, fully local, but only helps when the
   phone is on the shop LAN.
2. **Remote-capable with device-held encryption.** The phone can send required job
   data from elsewhere; the cloud only carries encrypted payloads; keys are held by
   the phone and shop PC after setup.
3. **Hybrid.** LAN when available, encrypted remote path when not.

These are genuinely different products. Pick which one this shop actually needs
first, because the key-management story and the least-privilege payload depend on
that choice.

## Why this is better than trying to make the phone do everything

- Templates, layout, print setup, and printer access already live on the shop
  PC. Duplicating them on the phone is more work and more drift.
- The phone's main job here is **input and trigger**, not full template rendering.
- Labels are a special case because values change; the clean split is:
  - phone: capture value, choose label kind;
  - PC: render label + print.

## Open questions to decide before building

- Which option above is the real target: LAN-only, encrypted remote control, or hybrid?
- Exactly what counts as "required data" for a remote bill job? Full print data,
  or enough for the PC to reproduce the print from local state?
- Exactly what counts as "required data" for a remote label job? The label value,
  the label kind, and anything else?
- How are keys set up and stored on the phone and shop PC, and what happens when one
  device is replaced or reinstalled?
- Is a shared relay acceptable if it cannot read the payloads, or do you want to
  avoid any cloud dependency for print jobs at all?
- How often is the phone actually outside the shop LAN — enough to justify the
  remote path?
- Do we want phone-side barcode scanning, or will values come from another source
  (manual entry, POS, lookup)?
- Who operates the phone — the father, or someone else helping him?
- Which printers: the same shop receipt printer, a label printer, or more than one?

## Next step

Pick one option from the practical list above and write the next focused decision
doc:

- **LAN-only path:** the LAN service contract (endpoints, printer discovery, label
  value payload, auth model) plus the phone app surface.
- **Encrypted remote-control path:** the required-data payload, the encryption model,
  key setup on phone and shop PC, and how the cloud relay is used only as a transport
  for opaque messages.
- **Hybrid path:** when each lane is used and how the phone decides which one to
  use.

Related:

- [[01 Architecture]] — the app's layered map (helps decide where a local print
  service or phone trigger fits)
- [[09 Template Download & Upload]] — the templates involved are the same ones
  exported/imported here helpful for moving templates onto the shop PC

## Network diagrams (what each path actually looks like)

### LAN-only (same network)

```
phone (LAN) --> shop PC LAN service --> template engine --> printer
```

The phone, PC, and printer are on the same local network. No internet is involved
in the print path.

### Remote-capable, encrypted (different networks)

```
phone --> encrypted job payload --> cloud relay --> shop PC --> decrypt --> local render --> printer
```

The internet carries only the required encrypted job data. The shop PC decrypts and
does the real print work locally. The cloud relay is used as a transport only.

If even an encrypted remote path is not acceptable for this shop, then remote control
is not a fit and the LAN-only path is the honest one.

### Two-way offline handling (optional, harder)

If the shop wants the phone to work when neither side can reach the other right now,
then a queue-and-sync model is a third option: the phone queues a job and the shop
PC applies it when it can receive it. That pushes more state into the phone and/or a
sync point, and is a bigger design decision than the two main paths above.
