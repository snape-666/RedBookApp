# Taste
- Communicates in Chinese; prefers responses and discussion in Chinese. Confidence: 0.8
- Prefers to replicate the structure and layout logic of an existing screen when creating a new similar page, to keep the UI consistent across related screens. Confidence: 0.9
- Prefers using the project's theme color helper functions (e.g., getOutline, getOnSurfaceSecondary, getOnSurfaceTertiary) rather than hardcoded colors. Confidence: 0.9
- When specifying a UI screen, gives precise pixel-level specs (exact dp spacing, corner radii, max-width ratios, color tokens) and expects the implementation to match them exactly. Confidence: 0.85
- Prefers compact controls that hug their content: input bars should be roughly text height plus small padding (e.g., 5dp vertical), not the taller default Material paddings, and sibling controls (e.g., a send button next to an input field) should be equal height. Confidence: 0.6
- Expects standard keyboard UX on input/chat screens: the IME keyboard pushes the content up (imePadding) and tapping blank space dismisses the keyboard. Confidence: 0.7
- For pages backed by cloud data, expects a ViewModel + ViewModelFactory wired through navigation, with optimistic UI updates that sync to the backend. Confidence: 0.6
