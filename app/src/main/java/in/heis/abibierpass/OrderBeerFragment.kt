package `in`.heis.abibierpass


import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_selectbeer.view.*
import kotlinx.android.synthetic.main.fragment_order_beer.*

class OrderBeerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_beer, container, false)
    }

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity!!.nav_view.menu.findItem(R.id.nav_orderbeer).isChecked = true
        activity!!.title = "Bier bestellen"
        val token = context!!.getSharedPreferences(key, Context.MODE_PRIVATE)
        var amount = 0
        val userRef = db.collection("Nutzer").document(auth.currentUser!!.uid)
        progressbar.visibility = View.VISIBLE

        val animator = ValueAnimator()
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                progressbar.visibility = View.INVISIBLE
                btn_orderbeer.isEnabled = true
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        for (readFrom in arrayOf("Nutzer", "NutzerVon")) {
            db.collection("Transaktionen").whereEqualTo(readFrom, userRef).get()
                .addOnSuccessListener { result ->
                    for (transaction in result) {
                        val transAmount = transaction.data["Betrag"].toString().toInt()
                        if (!((readFrom == "NutzerVon") and (transAmount > 0))) amount += transAmount

                    }
                }
                .addOnCompleteListener {
                    animator.setObjectValues(0, amount)
                    animator.addUpdateListener { animation ->
                        txt_coins.text = animation.animatedValue.toString()
                    }
                    animator.duration = 750
                    animator.start()
                }
        }
        btn_lastorder.setOnClickListener {
            db.collection("Transaktionen").whereLessThan("Betrag", 0).orderBy("Betrag")
                .whereEqualTo("NutzerVon", userRef).orderBy("Datum", Query.Direction.DESCENDING)
                .limit(1).get()
                .addOnSuccessListener { result ->
                    for (transaction in result) {
                        val date = transaction.data["Datum"] as Timestamp
                        val order = transaction.data["Auswahl"]
                        val state = transaction.data["Status"].toString().toInt()
                        BottomSheetFragment().show(
                            requireFragmentManager(),
                            BottomSheetFragment.FRAGMENT_TAG
                        )
                        BottomSheetFragment.sheet_body_txt =
                            "Bestellung: " + order + "\nZeitpunkt: " + date.toDate() + "\nStatus: " + CustomConvert().transStateToString(
                                state
                            )
                        BottomSheetFragment.sheet_head_txt = "Bestellbestätigung: Letzte Bestellung"
                        BottomSheetFragment.sheet_qr_content = transaction.id

                    }
                }
        }

        btn_orderbeer.setOnClickListener {
            val vulgo = token.getString("vulgo", "")
            val transInfo = hashMapOf(
                "Status" to 0,
                "Datum" to Timestamp.now(),
                "NutzerVon" to db.collection("Nutzer").document(
                    auth.currentUser!!.uid
                ),
                "Nutzer" to "Bierkasse",
                "Betrag" to -1
            )
            val mDialogView =
                LayoutInflater.from(context).inflate(R.layout.dialog_selectbeer, null)
            val mAlertDialogBuilder = MaterialAlertDialogBuilder(context)
                .setView(mDialogView)
                .setTitle("Auswahl")
            val mAlertDialog = mAlertDialogBuilder.show()

            fun manageOrder(beerType: String) {
                mAlertDialog.dismiss()
                if (amount > 0) {
                    if (mDialogView.switch_confirmedbeer.isChecked) transInfo["Status"] = 10
                    transInfo["Auswahl"] = beerType
                    db.collection("Transaktionen").document()
                        .set(transInfo)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) return@addOnCompleteListener
                            SelectMenu(R.id.nav_orderbeer, drawer_layout, activity).change()

                            btn_lastorder.performClick()

                            notifyFox(beerType, vulgo!!)
                        }
                } else Toast.makeText(
                    context,
                    "Du hast nicht genug Biercoins! Für weiterer Informationen wende dich an den Bierwart.",
                    Toast.LENGTH_LONG
                ).show()
            }

            mDialogView.btn_beerhell.setOnClickListener {
                val beerTyp = "Augustiner: Hell"
                manageOrder(beerTyp)
            }
            mDialogView.btn_beeredelstoff.setOnClickListener {
                val beerTyp = "Augustiner: Edelstoff"
                manageOrder(beerTyp)
            }
            mDialogView.btn_beertoast.setOnClickListener {
                val beerTyp = "Budentoast"
                manageOrder(beerTyp)
            }
            mDialogView.btn_beerweizen.setOnClickListener {
                val beerTyp = "Franziskaner: Weissbier"
                manageOrder(beerTyp)
            }
            mDialogView.btn_beerblau.setOnClickListener {
                val beerTyp = "Franziskaner: Alkoholfrei"
                manageOrder(beerTyp)
            }
            mDialogView.btn_beerradler.setOnClickListener {
                val beerTyp = "Radler"
                manageOrder(beerTyp)
            }
        }
    }

    private fun notifyFox(beer: String, vulgo: String) {
        MainActivity().sendNotification(
            context!!,
            "Bestellungen",
            "Neue Bestellung",
            "$beer für $vulgo"
        )
    }
}
