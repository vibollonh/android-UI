package com.example.baseui.sample

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.example.baseui.ZoomConfig
import com.example.baseui.ZoomDismissGesture
import com.example.baseui.views.BaseZoomTransition
import com.example.baseui.views.dismissZoom
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

/** Wallet cards for the View demo (same data as the Compose one, drawable icons). */
private data class XmlWallet(val name: String, val amount: String, val color: Int, val icon: Int)

private val xmlWallets = listOf(
    XmlWallet("Savings", "$12,480", 0xFF00897B.toInt(), R.drawable.ic_star),
    XmlWallet("Travel", "$3,210", 0xFF5E35B1.toInt(), R.drawable.ic_flag),
    XmlWallet("Bills", "$860", 0xFFEF6C00.toInt(), R.drawable.ic_edit),
    XmlWallet("Groceries", "$420", 0xFF8D6E63.toInt(), R.drawable.ic_qr),
    XmlWallet("Gifts", "$150", 0xFFD81B60.toInt(), R.drawable.ic_star),
    XmlWallet("Health", "$95", 0xFF1E88E5.toInt(), R.drawable.ic_check),
)

@Composable
fun ZoomXmlDemo() = XmlLayout(R.layout.demo_zoom_xml, ::bindZoom)

private fun bindZoom(root: View) {
    val activity = root.context as FragmentActivity
    val grid = root.findViewById<LinearLayout>(R.id.grid)
    val down = root.findViewById<MaterialSwitch>(R.id.drag_down)
    val right = root.findViewById<MaterialSwitch>(R.id.drag_right)
    val inflater = LayoutInflater.from(activity)
    val density = activity.resources.displayMetrics.density

    xmlWallets.chunked(2).forEachIndexed { rowIndex, pair ->
        val row = LinearLayout(activity)
        pair.forEachIndexed { i, wallet ->
            val card = inflater.inflate(R.layout.wallet_card, row, false)
            card.background = GradientDrawable().apply { setColor(wallet.color); cornerRadius = 20 * density }
            card.findViewById<ImageView>(R.id.icon).setImageResource(wallet.icon)
            card.findViewById<TextView>(R.id.name).text = wallet.name
            card.findViewById<TextView>(R.id.amount).text = wallet.amount
            (card.layoutParams as LinearLayout.LayoutParams).marginStart = if (i > 0) (12 * density).toInt() else 0
            card.setOnClickListener {
                val config = ZoomConfig(
                    dismissGestures = buildSet {
                        if (down.isChecked) add(ZoomDismissGesture.Down)
                        if (right.isChecked) add(ZoomDismissGesture.Right)
                    },
                    sourceCornerRadiusDp = 20f,
                )
                BaseZoomTransition.present(activity, card, WalletDetailFragment.newInstance(xmlWallets.indexOf(wallet)), config)
            }
            row.addView(card)
        }
        grid.addView(row, 1 + rowIndex, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (12 * density).toInt() })
    }
}

/** The zoomed screen: hosts its own back stack of child fragments. */
class WalletDetailFragment : Fragment(R.layout.fragment_wallet_detail) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            childFragmentManager.commit { replace(R.id.wallet_host, WalletHomeFragment().apply { arguments = this@WalletDetailFragment.arguments }) }
        }
    }

    fun push(fragment: Fragment) = childFragmentManager.commit {
        setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
        replace(R.id.wallet_host, fragment)
        addToBackStack(null)
    }

    companion object {
        fun newInstance(index: Int) = WalletDetailFragment().apply { arguments = Bundle().apply { putInt("wallet", index) } }
    }
}

class WalletHomeFragment : Fragment(R.layout.fragment_wallet_home) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val wallet = xmlWallets[requireArguments().getInt("wallet")]
        val host = parentFragment as WalletDetailFragment
        view.setBackgroundColor(wallet.color)
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = wallet.name
            menu.add("Close").setIcon(R.drawable.ic_close).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener { dismissZoom(); true }
        }
        view.findViewById<ImageView>(R.id.icon).setImageResource(wallet.icon)
        view.findViewById<TextView>(R.id.amount).text = wallet.amount

        val rows = view.findViewById<LinearLayout>(R.id.rows)
        val transactions = listOf("Coffee", "Taxi", "Hotel", "Market", "Flight", "Pharmacy", "Books", "Dinner")
            .mapIndexed { i, t -> "$t · ${wallet.name}" to "-$${29 + i * 17}.00" }
        rows.addView(navigationRow("Transactions") {
            host.push(KeyValueFragment.newInstance("Transactions", transactions, pushesDetail = true))
        })
        rows.addView(navigationRow("Statements") {
            host.push(KeyValueFragment.newInstance("Statements", listOf(
                "September 2026" to "$1,240.00", "August 2026" to "$980.50", "July 2026" to "$1,105.20", "June 2026" to "$860.00")))
        })
        walletDetails.forEach { (label, value) -> rows.addView(valueRow(label, value)) }
        rows.addView(TextView(requireContext()).apply {
            text = "Drag down or right, or use the back gesture, to close. Pushed screens don't close by dragging: go back first."
            setTextColor(Color.argb(204, 255, 255, 255))
            textSize = 12f
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun navigationRow(title: String, onClick: () -> Unit) = LinearLayout(requireContext()).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(16), 0, dp(16))
        addTopDivider()
        addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_chevron); alpha = 0.7f })
        setOnClickListener { onClick() }
    }

    private fun valueRow(label: String, value: String) = LinearLayout(requireContext()).apply {
        setPadding(0, dp(16), 0, dp(16))
        addTopDivider()
        addView(TextView(context).apply { text = label; setTextColor(Color.WHITE); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = value; setTextColor(Color.WHITE); textSize = 16f })
    }

    /** A thin white line above the row (drawn as the row's foreground). */
    private fun View.addTopDivider() {
        foreground = android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(Color.argb(102, 255, 255, 255)) })).apply {
            setLayerHeight(0, dp(1))
            setLayerGravity(0, android.view.Gravity.TOP)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

/** A pushed list of label / value rows; optionally each row pushes a detail list. */
class KeyValueFragment : Fragment(R.layout.fragment_key_value) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val labels = args.getStringArrayList("labels").orEmpty()
        val values = args.getStringArrayList("values").orEmpty()
        val pushesDetail = args.getBoolean("pushesDetail")
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = args.getString("title")
            setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        }
        val rows = view.findViewById<LinearLayout>(R.id.rows)
        val onSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
        val variant = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
        labels.zip(values).forEach { (label, value) ->
            rows.addView(LinearLayout(requireContext()).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(TextView(context).apply { text = label; setTextColor(onSurface); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply { text = value; setTextColor(variant); textSize = 14f })
                if (pushesDetail) {
                    isClickable = true
                    setOnClickListener {
                        (parentFragment as WalletDetailFragment).push(newInstance(label, listOf(
                            "Amount" to value, "Date" to "Oct 8, 2026", "Status" to "Completed", "Reference" to "TX-4810")))
                    }
                }
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun newInstance(title: String, rows: List<Pair<String, String>>, pushesDetail: Boolean = false) = KeyValueFragment().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putStringArrayList("labels", ArrayList(rows.map { it.first }))
                putStringArrayList("values", ArrayList(rows.map { it.second }))
                putBoolean("pushesDetail", pushesDetail)
            }
        }
    }
}
