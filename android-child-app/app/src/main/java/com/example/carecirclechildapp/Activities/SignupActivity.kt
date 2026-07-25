package com.example.carecirclechildapp.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecirclechildapp.MyApplication
import com.example.carecirclechildapp.databinding.ActivitySignunpBinding
import com.example.carecirclechildapp.modals.Children
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignunpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
            binding = ActivitySignunpBinding.inflate(layoutInflater)
            setContentView(binding.root)

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
            setupEditTextAnimations()

            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            binding.signupBtn.setOnClickListener {
                handleSignup()
            }

            binding.loginRedirect.setOnClickListener {
                safeStartActivity(LoginActivity::class.java, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("Unexpected error occurred. Please try again.")
        }
    }

    private fun handleSignup() {
        // Extract input values
        val email = binding.emailText.text.toString().trim()
        val password = binding.passwordText.text.toString().trim()
        val firstName = binding.firstName.text.toString().trim()
        val lastName = binding.lastName.text.toString().trim()

        // Input validations
        if (firstName.isEmpty() || lastName.isEmpty()) {
            showErrorToast("First and last name cannot be empty")
            return
        }
        if (firstName.length > 20 || lastName.length > 20) {
            showErrorToast("Names must be less than 50 characters")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showErrorToast("Invalid email address")
            return
        }
        if (email.length > 40) {
            showErrorToast("Email is too long")
            return
        }
        if (password.length < 8) {
            showErrorToast("Password must be at least 8 characters")
            return
        }
        if (password.length > 28) {
            showErrorToast("Password is too long")
            return
        }
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$".toRegex())) {
            showErrorToast("Password must contain letters and numbers, and may include special chars")
            return
        }

        // Show loading state
        setLoadingState(true)

        // Firebase signup using coroutines for cleaner async handling
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Create user with email and password
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    val userId = user.uid
                    val childName = "$firstName $lastName"
                    val child = Children(childName, email, "******", userId)

                    // Store user profile in Firestore
                    db.collection("children_data")
                        .document(userId)
                        .collection("profile")
                        .add(child)
                        .await()

                    // Send email verification
                    user.sendEmailVerification().await()
                    showErrorToast("Verification email sent. Please verify your email before logging in.")

                    // Sign out to enforce email verification
                    auth.signOut()

                    // Navigate to LoginActivity
                    safeStartActivity(LoginActivity::class.java, true)
                } else {
                    showErrorToast("Signup failed: User not created")
                }
            } catch (e: FirebaseAuthException) {
                // Handle specific Firebase auth errors
                when (e.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> showErrorToast("This email is already registered")
                    "ERROR_INVALID_EMAIL" -> showErrorToast("Invalid email format")
                    "ERROR_WEAK_PASSWORD" -> showErrorToast("Password is too weak")
                    else -> showErrorToast("Signup failed: ${e.message}")
                }
            } catch (e: Exception) {
                showErrorToast("Error: ${e.message}")
            } finally {
                // Hide loading state
                setLoadingState(false)
            }
        }
    }
    private fun setupEditTextAnimations() {
        val inputs = listOf(binding.emailField, binding.passwordField,binding.firstNameField,binding.lastNameField)

        inputs.forEach { editText ->
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus || !editText.editText?.text.isNullOrEmpty()) {
                    editText.animate()
                        ?.translationY(-20f) // move up
                        ?.setDuration(200)
                        ?.start()
                } else {
                    editText.animate()
                        ?.translationY(0f) // move back down
                        ?.setDuration(200)
                        ?.start()
                }
            }

            editText?.editText?.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!s.isNullOrEmpty()) {
                        editText.animate()
                            ?.translationY(-40f)
                            ?.setDuration(200)
                            ?.start()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.isNullOrEmpty() && editText.editText?.hasFocus()!!.not()) {
                        editText.animate()
                            ?.translationY(0f)
                            ?.setDuration(200)
                            ?.start()
                    }
                }
            })
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.signupBtn.isEnabled = !isLoading
    }

    private fun safeStartActivity(target: Class<*>, finishCurrent: Boolean = false) {
        try {
            startActivity(Intent(this, target))
            if (finishCurrent) finish()
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("Navigation error. Please try again.")
        }
    }

    private fun showErrorToast(message: String) {
        if (!isFinishing) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}