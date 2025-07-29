# Config Card UI Improvements Plan

## 1. Enhanced UI Schema Generation
Instead of a flat vertical layout, generate a hierarchical UI schema:
- Group nested objects into collapsible panels/cards
- Add section headers from property names (e.g., "Parsing Options", "Error Handling")
- Support tabs for top-level groupings when there are many sections

## 2. Better Widget Selection Based on Schema
- **Booleans with `x-ui-widget: "switch"`** → Toggle switches instead of checkboxes
- **Integers with min/max** → Slider with number input
- **Strings with examples** → Dropdown with examples + custom input option
- **Arrays** → Tag input or multi-select based on item type
- **Long descriptions** → Info icon with tooltip

## 3. Visual Hierarchy
- **Section Cards**: Each top-level object property gets its own card
- **Indentation**: Nested properties are visually indented
- **Typography**: Section headers are larger, field labels are medium, descriptions are smaller
- **Colors**: Use subtle backgrounds for sections, borders for separation

## 4. Field Enhancements
- **Required fields**: Visual indicator (red asterisk or badge)
- **Default values**: Show in placeholder or as reset button
- **Validation**: Real-time validation with error messages
- **Help text**: Show descriptions below fields or as tooltips
- **Examples**: Show as placeholder text or in a dropdown

## 5. Layout Options
- **Responsive grid**: Use 2-column layout on wide screens for better space usage
- **Collapsible sections**: Allow users to collapse/expand sections
- **Advanced mode**: Hide advanced options by default with a toggle

## 6. Custom Renderers for Common Patterns
- **File size fields** (like maxContentLength): Show with unit selector (KB, MB, GB)
- **Duration fields** (like parseTimeoutSeconds): Show as number + unit (seconds, minutes)
- **MIME type arrays**: Specialized selector with common types
- **Config ID**: Suggest naming patterns based on examples

## Implementation Strategy:
1. Create custom JSONForms renderers for enhanced widgets
2. Enhance UI schema generation to create hierarchical layouts
3. Add CSS for better visual hierarchy
4. Use schema annotations (x-ui-*) for customization