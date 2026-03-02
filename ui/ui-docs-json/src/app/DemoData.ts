export const DEMO_JSON_SCHEMA: any = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "Widget",
    "description": "A widget object used for demonstration purposes.",
    "type": "object",
    "required": ["id", "name"],
    "properties": {
        "id": {
            "type": "integer",
            "description": "The unique identifier for the widget."
        },
        "name": {
            "type": "string",
            "description": "The name of the widget.",
            "minLength": 1,
            "maxLength": 256
        },
        "description": {
            "type": "string",
            "description": "An optional description of the widget."
        },
        "category": {
            "type": "string",
            "description": "The category this widget belongs to.",
            "enum": ["electronics", "mechanical", "software", "other"]
        },
        "tags": {
            "type": "array",
            "description": "A list of tags associated with the widget.",
            "items": {
                "type": "string"
            }
        },
        "dimensions": {
            "type": "object",
            "description": "Physical dimensions of the widget.",
            "properties": {
                "width": {
                    "type": "number",
                    "description": "Width in centimeters."
                },
                "height": {
                    "type": "number",
                    "description": "Height in centimeters."
                },
                "depth": {
                    "type": "number",
                    "description": "Depth in centimeters."
                }
            },
            "required": ["width", "height"]
        },
        "createdAt": {
            "type": "string",
            "format": "date-time",
            "description": "The date and time when the widget was created."
        },
        "active": {
            "type": "boolean",
            "description": "Whether the widget is currently active.",
            "default": true
        }
    }
};
