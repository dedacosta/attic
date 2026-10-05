# Translations

One file per language: `en.json`, `pt.json`, `fr.json`. Each has the same keys, and the build
fails if a key of `en.json` is missing in another file.

- **Placeholders** in braces are filled in by the app and must stay as they are:
  `"Children of {name}"` → `"Filhos de {name}"`.
- **Texts that depend on a number** have one form per plural category of the language, chosen
  on `{count}`. English and Portuguese use `one` (1) and `other`; French also uses `one` for 0.
  A form for an exact number, such as `"=0"`, wins over the categories:

  ```json
  "itemCount": { "one": "{count} item", "other": "{count} items" }
  ```

  A text that does not change with the number can stay a plain string, even if it has `{count}`.
- **Lists** such as `sexes`, `documentTypes` and `rooms` map the app's codes to names; translate
  the names, not the codes.
- ` ` is a non-breaking space, as French puts before `:` and inside `« »`.

To add a language, copy `en.json`, translate it, and register it in `index.tsx`
(`Language`, `LANGUAGES`, `LANGUAGE_NAMES` and `MESSAGES`).
