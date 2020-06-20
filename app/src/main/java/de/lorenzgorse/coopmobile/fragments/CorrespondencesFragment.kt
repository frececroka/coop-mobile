package de.lorenzgorse.coopmobile.fragments

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.*
import kotlinx.android.synthetic.main.fragment_correspondences.*
import java.text.SimpleDateFormat
import java.util.*

class CorrespondencesFragment : Fragment() {

    private val dateFormat = SimpleDateFormat("EEE, d. MMMM yyyy", Locale.getDefault())

    private lateinit var inflater: LayoutInflater
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: CorrespondencesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        viewModel = ViewModelProvider(this).get(CorrespondencesViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_correspondences, container, false)
    }

    override fun onStart() {
        super.onStart()
        analytics.setCurrentScreen(requireActivity(), "Correspondences", null)
        viewModel.data.observe(this, Observer(::setData))
    }

    private fun setData(data: Value<List<Correspondence>>?) {
        when (data) {
            is Value.Initiated ->
                showLoading()
            is Value.Progress ->
                loading.setProgress(data.current, data.total)
            is Value.Failure ->
                handleLoadDataError(
                    data.error,
                    ::showNoNetwork,
                    ::showUpdateNecessary,
                    ::showPlanUnsupported,
                    ::goToLogin)
            is Value.Success ->
                onSuccess(data.value)
        }
    }

    private fun showLoading() {
        loading.visibility = View.VISIBLE
        loading.makeIndeterminate()
        layContent.visibility = View.GONE
    }

    private fun showNoNetwork() {
        Toast.makeText(context, R.string.no_network, Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    private fun showUpdateNecessary() {
        Toast.makeText(context, R.string.update_necessary, Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    private fun showPlanUnsupported() {
        Toast.makeText(context, R.string.plan_unsupported, Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    private fun goToLogin() {
        analytics.logEvent("go_to_login", null)
        findNavController().navigate(R.id.action_correspondences_to_login)
    }

    private fun onSuccess(data: List<Correspondence>) {
        linCorrespondences.removeAllViews()
        for (correspondence in data) {
            val productItemView = inflater.inflate(
                R.layout.correspondence, linCorrespondences, false)
            val txtDate = productItemView.findViewById<TextView>(R.id.txtDate)
            val txtSubject = productItemView.findViewById<TextView>(R.id.txtSubject)
            val txtMessage = productItemView.findViewById<TextView>(R.id.txtMessage)

            txtDate.text = dateFormat.format(correspondence.header.date)
            txtSubject.text = correspondence.header.subject
            txtMessage.text = correspondence.message
            linCorrespondences.addView(productItemView)
        }
        loading.visibility = View.GONE
        layContent.visibility = View.VISIBLE
    }

}

class CorrespondencesViewModel(
    app: Application
): ApiDataViewModel<List<Correspondence>>(app, { progress -> { client ->
    val correspondences = client.getCorrespondeces()
    correspondences.mapIndexed { i, c ->
        client.augmentCorrespondence(c).also {
            progress(i, correspondences.size) }
    }
} })
