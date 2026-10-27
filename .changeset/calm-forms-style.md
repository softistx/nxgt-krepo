---
"stx-material": minor
---

Every interactive form part takes `style: Style = Style`, applied last. New API: `style` on `Autocomplete`, `SelectField`, `CopyField`, `Composer`, `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup`, `CheckboxGroup`, `Checklist`, both `SliderField` overloads, `QuantityField`, `InlineEdit`, `TagField`, `OtpField`, `UploadField` and `ThemeToggle`; `StxStyles.toggle` and `StxStyles.upload`; `Autocomplete(isError, supportingText)` like the other fields. Parameter placement: `style` sits immediately before a trailing lambda (`optionLabel` on `Autocomplete`, `SelectField`, `RadioGroup`, `CheckboxGroup`; `format` on `SliderField`), so `RadioGroup(…) { it.label }` still compiles while a positional call that passed that lambda right after `enabled` must name it. Visible default changes:

- Press: the rows of `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup` and `CheckboxGroup` scale to 0.97 under the pointer; `UploadField` scales to 0.99.
- Disabled: `UploadField` and `OtpField` fade to the 0.38 disabled alpha, and a disabled `InlineEdit` fades its text.
- `Autocomplete` shows a complaint in the error colours when `isError` is set.
