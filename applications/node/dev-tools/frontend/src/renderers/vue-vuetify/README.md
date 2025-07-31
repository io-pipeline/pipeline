# Vue-Vuetify Renderers (Temporary)

## Why This Directory Exists

This directory contains a temporary copy of the @jsonforms/vue-vuetify renderers with updated import paths for Vuetify 3.9.x compatibility.

## The Issue

Between Vuetify 3.6 and 3.9, labs components were moved from `vuetify/labs/` to `vuetify/components/`:
- `vuetify/labs/VNumberInput` → `vuetify/components/VNumberInput`
- `vuetify/labs/VTimePicker` → `vuetify/components/VTimePicker`

The official @jsonforms/vue-vuetify package (v3.6.0) still uses the old import paths, causing build failures with Vuetify 3.9.x.

## The Fix

The JSONForms team has already fixed this issue in their repository:
- **Branch**: `issues/2434-vuertify-version-update`
- **PR**: https://github.com/eclipsesource/jsonforms/pull/2435 (closed, superseded by updates to Vuetify ^3.9)
- **Commit**: `cfeca5ab` - "deps: Update to Vuetify ^3.9 and Vue ^3.5"

## Why We Can't Use the Branch Directly

We attempted to install directly from the GitHub branch:
```bash
pnpm add "github:eclipsesource/jsonforms#issues/2434-vuertify-version-update&path:/packages/vue-vuetify"
```

However, this installs source files while the package expects built files in a `lib/` directory. The package's `exports` field doesn't allow importing from `/src/`.

## Components Affected

This affects any component using JSONForms with Vuetify renderers:
- `UniversalConfigCard` - Used for dynamic configuration UI
- `PipeDocEditor` - Used for protobuf-based form editing
- `VuetifyConfigCard` - Basic config card component
- Any future components in shared libraries that need type-safe form rendering

## Next Steps

1. **Keep using this temporary copy** until the official release
2. **Monitor the JSONForms releases** for the Vuetify 3.9 update
3. **When released**, update package.json to use the official version:
   ```json
   "@jsonforms/vue-vuetify": "^3.7.0"  // or whatever version includes the fix
   ```
4. **Remove this directory** once the official package works

## Testing

When the official package is released, test with:
1. Module configuration forms
2. Protobuf-based editors
3. Any components using number inputs or time pickers

## Contact

Consider reaching out to the JSONForms team to express interest in the Vuetify 3.9 update release.