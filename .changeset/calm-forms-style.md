---
"stx-material": minor
---

Every interactive form part takes `style: Style = Style`, applied last. New API: `style` on `Autocomplete`, `SelectField`, `CopyField`, `Composer`, `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup`, `CheckboxGroup`, `Checklist`, both `SliderField` overloads, `QuantityField`, `InlineEdit`, `TagField`, `OtpField`, `UploadField` and `ThemeToggle`; `StxStyles.toggle` and `StxStyles.upload`; `Autocomplete(isError, supportingText)` like the other fields. Parameter placement: `style` comes after `enabled` (and after `isError` and `supportingText` on `Autocomplete`), so positional calls keep their meaning; it sits immediately before a trailing lambda (`optionLabel` on `Autocomplete`, `SelectField`, `RadioGroup`, `CheckboxGroup`; `format` on `SliderField`), so `RadioGroup(…) { it.label }` still compiles, and only a positional call that passed that named lambda must name it. Visible default changes:

- Press: the rows of `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup` and `CheckboxGroup` and the `UploadField` well scale to 0.99 under the pointer.
- Disabled: `UploadField` and `OtpField` fade to the 0.38 disabled alpha, and a disabled `InlineEdit` fades its text (before: no fade).
- Disabled `TextField` and `TextareaField`: the extra 0.38 fade is gone, M3's own dimming remains (before: dimmed twice).
- `Autocomplete(isError, supportingText)` shows a complaint in the error colours; `isError` and `supportingText` come after `enabled`.
- `Autocomplete` shows a complaint in the error colours when `isError` is set.
