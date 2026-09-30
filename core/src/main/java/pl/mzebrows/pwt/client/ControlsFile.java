package pl.mzebrows.pwt.client;

/**
 * {@code controls.properties} as its keys are written, bound by {@code ConfigBinder} under {@code
 * controls}: a binding is a component here and a line there, and nothing else. {@link ControlsConfig}
 * builds what the input reads from it. A key or a mouse button is its name until then, checked before
 * binding ({@link ControlsConfig#parse}).
 */
record ControlsFile(Key key, Mouse mouse, Pad pad, Ui ui) {

    record Key(
            String moveUp,
            String moveUpAlias,
            String moveDown,
            String moveDownAlias,
            String moveLeft,
            String moveLeftAlias,
            String moveRight,
            String moveRightAlias,
            String grenade,
            String action,
            String drag,
            String zoom,
            String rollCall,
            String orderFollowMe,
            String orderForward,
            String orderDefence,
            String orderCancel,
            String draft,
            String crateClose) {}

    record Mouse(String fire, String strike) {}

    record Pad(
            GamepadAxis moveX,
            GamepadAxis moveY,
            GamepadAxis aimX,
            GamepadAxis aimY,
            GamepadButton fire,
            GamepadAxis fireTrigger,
            GamepadButton strike,
            GamepadAxis strikeTrigger,
            GamepadButton grenade,
            GamepadAxis grenadeTrigger,
            GamepadButton action,
            GamepadAxis actionTrigger,
            GamepadButton drag,
            GamepadAxis dragTrigger,
            GamepadButton zoom,
            GamepadAxis zoomTrigger,
            GamepadButton pause,
            GamepadAxis pauseTrigger,
            GamepadButton orderFollowMe,
            GamepadAxis orderFollowMeTrigger,
            GamepadButton orderForward,
            GamepadAxis orderForwardTrigger,
            GamepadButton orderDefence,
            GamepadAxis orderDefenceTrigger,
            GamepadButton orderCancel,
            GamepadAxis orderCancelTrigger,
            GamepadButton draft,
            GamepadAxis draftTrigger,
            int leftTriggerAxis,
            int rightTriggerAxis,
            boolean triggerRestsAtMinusOne,
            float moveDeadZone,
            float aimDeadZone,
            float triggerThreshold) {}

    record Ui(UiKey key, UiPad pad, Nav nav) {}

    record UiKey(String confirm, String confirmAlias, String back, String backAlias) {}

    record UiPad(GamepadButton confirm, GamepadButton back) {}

    record Nav(
            float stickThreshold,
            float repeatDelaySeconds,
            float repeatIntervalSeconds,
            int pointerWakePixels,
            float scrollLinesPerSecond) {}
}
