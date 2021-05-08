package pt.tecnico.ulisboa.cnv.AutoScaler;

public class AutoScalerAction {
    private AutoScalerActionEnum action;  // The action can be NO_ACTION, INCREASE_FLEET, DECREASE_FLEET
    private int count = 0;      // The number of instances to affect with the specified action

    public AutoScalerAction(AutoScalerActionEnum action, int count) {
        this.action = action;
        this.count = count;
    }

    public AutoScalerAction(AutoScalerActionEnum action) {
        this.action = action;
    }

    public int getCount() {
        return count;
    }

    public AutoScalerActionEnum getAction() {
        return action;
    }
}
