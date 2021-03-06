package com.param.expensesio.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog.BUTTON_POSITIVE
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import com.facebook.login.LoginManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.FirebaseFirestore
import com.param.expensesio.MyViewModel
import com.param.expensesio.R
import com.param.expensesio.data.UserCategoryBackup
import com.param.expensesio.ui.AlertBackup
import com.param.expensesio.ui.ProfileViewPreference
import java.util.*


//data class Person(public var name: String, public var age: String)
//data class MyData(public var names: List<Person> = listOf())


class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: MyViewModel by viewModels()

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Display name, email and profile pic
        val user = firebaseAuth.currentUser!!

        val userProfilePref = findPreference<ProfileViewPreference>("profileImage")!!
            .setUserEmail(user.email!!)
            .setUserName(user.displayName!!)

        val url = user.photoUrl

        if (url != null) {
            userProfilePref.setImage(Objects.requireNonNull(url).toString())
        } else {
            firebaseAuth.getAccessToken(false).addOnSuccessListener {
                val nameInitials = viewModel.getNameInit(user.displayName!!)
                userProfilePref.setInit(nameInitials)
            }
        }

        // Category
        findPreference<Preference>("categories")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val action =
                    SettingsFragmentDirections.actionSettingsFragmentToManageCategoryFragment()
                findNavController().navigate(action)
                true
            }

        // Budgets
//        findPreference<Preference>("budgets")!!.onPreferenceClickListener =
//            Preference.OnPreferenceClickListener {
//                val action = SettingsFragmentDirections.actionSettingsFragmentToBudgetFragment()
//                findNavController().navigate(action)
//                true
//            }

        // Currency
        val useLocalCurrency = findPreference<SwitchPreferenceCompat>("useLocalCurrency")!!
        val currencySymbol = findPreference<EditTextPreference>("currencySymbol")!!

        // To remember previous state
        currencySymbol.isEnabled = !useLocalCurrency.isChecked

        useLocalCurrency.setOnPreferenceChangeListener { preference, _ ->
            val oldState = (preference as SwitchPreferenceCompat).isChecked
            currencySymbol.isEnabled = oldState
            true
        }

        // Sort History
        val sortHistoryBy = findPreference<ListPreference>("sortHistoryBy")!!
        sortHistoryBy.setOnPreferenceChangeListener { _, newValue ->
            sortHistoryBy.value = newValue.toString()
            true
        }

        // Backup Data
        findPreference<Preference>("backup")!!.setOnPreferenceClickListener {

            Log.d(javaClass.canonicalName, "backup button clicked ${viewModel.backupStat.value}")

            viewModel.readAllCategory(viewModel.userEmail()).observe(viewLifecycleOwner) {
                viewModel.backupUserCategories(UserCategoryBackup(it))
            }

            val dialogBackup = AlertBackup(requireActivity())

            val unwarranted = viewModel.getUnbackedUpExpenses(viewModel.userEmail())

            unwarranted.observe(viewLifecycleOwner) {
                val unbackedPastExpenses = it.filter { exp -> !exp.isFromNow() }
                if (unbackedPastExpenses.isNotEmpty()) {
                    dialogBackup.show()
                    Log.d(javaClass.canonicalName, "backing up takes place in view model")
                    viewModel.backupUserExpenses(unbackedPastExpenses)
                }
                unwarranted.removeObservers(viewLifecycleOwner)
            }



            viewModel.backupStat.observe(viewLifecycleOwner) {
                Log.d(javaClass.canonicalName, "backup result $it")
                if(it){
                    dialogBackup.dismiss()
                }
            }

            true
        }

        // Restore data
        findPreference<Preference>("restore")!!.setOnPreferenceClickListener {

            viewModel.restoreUserCategories()
            viewModel.restoreUserExpenses()


            true
        }

        // Account section
        findPreference<Preference>("logout")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {

                // Check sing in method to properly log out
                firebaseAuth.getAccessToken(false)
                    .addOnSuccessListener(object : OnSuccessListener<GetTokenResult> {

                        override fun onSuccess(p0: GetTokenResult?) {
                            Log.d("SettingsFragment", "Logout ${p0!!.signInProvider!!}")
                            when (p0.signInProvider) {
                                "password" -> emailPasswordSignOut()
                                "google.com" -> googleSignOut()
                                "facebook.com" -> facebookSignOut()
                                else -> throw IllegalArgumentException("Invalid signInProvider, error occurred while logging out using firebase")

                            }
                        }

                    })

                true
            }

        // About
        findPreference<Preference>("about")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                android.app.AlertDialog.Builder(requireContext()).create().apply {
                    setTitle("About")
                    setMessage("Design and created by Paramvir Singh")
                    setButton(BUTTON_POSITIVE, "Ok") { it, _ -> it.dismiss() }
                    show()
                }
                true
            }
    }


    private fun emailPasswordSignOut() {
        FirebaseAuth.getInstance().signOut()
        navigateToLogin()
    }

    private fun googleSignOut() {

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(requireActivity(), gso).signOut().addOnCompleteListener {
            FirebaseAuth.getInstance().signOut()
            navigateToLogin()
        }

    }

    private fun facebookSignOut() {
        FirebaseAuth.getInstance().signOut()
        LoginManager.getInstance().logOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val action = SettingsFragmentDirections.actionSettingsFragmentToLoginFragment()
        findNavController().navigate(action)
    }

}