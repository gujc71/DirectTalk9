package gujc.directtalk9;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

public class Util9 {
    private static final Util9 ourInstance = new Util9();

    static Util9 getInstance() {
        return ourInstance;
    }

    private Util9() {
    }

    public static void showMessage(Context context, String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }
}
