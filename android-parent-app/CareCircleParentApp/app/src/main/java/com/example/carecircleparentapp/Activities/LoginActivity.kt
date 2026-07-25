package com.example.carecircleparentapp.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecircleparentapp.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {
    private var binding: ActivityLoginBinding? = null
    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding?.root ?: return)

            ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            auth = FirebaseAuth.getInstance()
            val currentUser = auth?.currentUser

            // Auto-redirect if already logged in
            if (currentUser != null) {
                if (!currentUser.isEmailVerified) {
                    showErrorToast("Please verify your email before logging in.")
                    auth?.signOut()
                    initializeLoginUI()
                } else {
                    safeStartActivity(MainActivity::class.java, true)
                }
            } else {
                initializeLoginUI()
                setupEditTextAnimations()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("Unexpected error occurred. Please try again.")
        }
    }
    private fun setupEditTextAnimations() {
        val inputs = listOf(binding?.emailField,binding?.passwordField)

        inputs.forEach { editText ->
            editText?.setOnFocusChangeListener { _, hasFocus ->
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
    private fun initializeLoginUI() {
        binding?.signupRedirect?.setOnClickListener {
            safeStartActivity(SignupActivity::class.java, true)
        }

        binding?.loginBtn?.setOnClickListener {
            val email = binding?.emailInput?.text?.toString()?.trim() ?: ""
            val password = binding?.passwordInput?.text?.toString()?.trim() ?: ""

            // Validations
            if (email.isEmpty() || password.isEmpty()) {
                showErrorToast("Please fill both email and password fields")
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showErrorToast("Invalid email format")
                return@setOnClickListener
            }
            if (email.length > 100) {
                showErrorToast("Email is too long")
                return@setOnClickListener
            }
            if (password.length < 8) {
                showErrorToast("Password must be at least 8 characters")
                return@setOnClickListener
            }
            if (password.length > 128) {
                showErrorToast("Password is too long")
                return@setOnClickListener
            }

            // Disable button during login
            setLoadingState(true)

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val authResult = auth?.signInWithEmailAndPassword(email, password)?.await()
                    val user = authResult?.user
                    if (user != null) {
                        if (user.isEmailVerified) {
                            // ✅ Keep using the same Firestore path "parents -> parentId"
                            safeStartActivity(MainActivity::class.java, true)
                        } else {
                            showErrorToast("Please verify your email before logging in.")
                            auth?.signOut()
                        }
                    } else {
                        showErrorToast("Login failed: User not found")
                    }
                } catch (e: FirebaseAuthException) {
                    when (e.errorCode) {
                        "ERROR_INVALID_EMAIL" -> showErrorToast("Invalid email format")
                        "ERROR_WRONG_PASSWORD" -> showErrorToast("Incorrect password")
                        "ERROR_USER_NOT_FOUND" -> showErrorToast("No account found with this email")
                        "ERROR_TOO_MANY_REQUESTS" -> showErrorToast("Too many attempts. Try again later.")
                        else -> showErrorToast("Login failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    showErrorToast("Error: ${e.message}")
                } finally {
                    setLoadingState(false)
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding?.loginBtn?.isEnabled = !isLoading
    }

    private fun safeStartActivity(target: Class<*>, finishCurrent: Boolean = false) {
        try {
            val intent = Intent(this, target)
            startActivity(intent)
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

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}
