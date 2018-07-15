package gujc.directtalk9;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import gujc.directtalk9.model.UserModel;

public class LoginActivity extends AppCompatActivity {
    private Button loginBtn;
    private Button signupBtn;
    private EditText user_id;
    private EditText user_pw;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        user_id = findViewById(R.id.user_id);
        user_pw = findViewById(R.id.user_pw);
        loginBtn = findViewById(R.id.loginBtn);
        signupBtn = findViewById(R.id.signupBtn);

        loginBtn.setOnClickListener(loginClick);
        signupBtn.setOnClickListener(signupClick);

        sharedPreferences = getSharedPreferences("gujc", Activity.MODE_PRIVATE);
        String id = sharedPreferences.getString("user_id", "");
        if (!"".equals(id)) {
            user_id.setText(id);
        }
    }

    Button.OnClickListener loginClick = new View.OnClickListener() {
        public void onClick(View view) {
            if (!validateForm()) return;

            FirebaseAuth.getInstance().signInWithEmailAndPassword(user_id.getText().toString(), user_pw.getText().toString()).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        sharedPreferences.edit().putString("user_id", user_id.getText().toString()).commit();
                        Intent  intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Util9.showMessage(getApplicationContext(), task.getException().getMessage());
                    }
                }
            });
        }
    };

    Button.OnClickListener signupClick = new View.OnClickListener() {
        public void onClick(View view) {
            if (!validateForm()) return;
            final String id = user_id.getText().toString();

            FirebaseAuth.getInstance().createUserWithEmailAndPassword(user_id.getText().toString(), user_pw.getText().toString()).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        sharedPreferences.edit().putString("user_id", id).commit();
                        String uid = FirebaseAuth.getInstance().getUid();
                        UserModel userModel = new UserModel();
                        userModel.setUid(uid);
                        userModel.setUserid(id);
                        userModel.setUsernm(extractIDFromEmail(id));
                        userModel.setUsermsg("...");
                        FirebaseDatabase.getInstance().getReference().child("users").child(uid).setValue(userModel).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                Intent  intent = new Intent(LoginActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        });
                    } else {
                        Util9.showMessage(getApplicationContext(), task.getException().getMessage());
                    }
                }
            });
        }
    };

    String extractIDFromEmail(String email){
        String[] parts = email.split("@");
        return parts[0];
    }

    private boolean validateForm() {
        boolean valid = true;

        String email = user_id.getText().toString();
        if (TextUtils.isEmpty(email)) {
            user_id.setError("Required.");
            valid = false;
        } else {
            user_id.setError(null);
        }

        String password = user_pw.getText().toString();
        if (TextUtils.isEmpty(password)) {
            user_pw.setError("Required.");
            valid = false;
        } else {
            user_pw.setError(null);
        }

        return valid;
    }
}
