package com.param.expensesio.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.param.expensesio.History
import com.param.expensesio.HistoryHeader
import com.param.expensesio.MyViewModel
import com.param.expensesio.R
import com.param.expensesio.data.Expense
import com.param.expensesio.databinding.FragmentHistoryBinding
import com.param.expensesio.viewbehavior.ViewBehavior
import com.xwray.groupie.ExpandableGroup
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import jp.wasabeef.recyclerview.animators.ScaleInBottomAnimator
import java.time.LocalDate


class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private val viewModel: MyViewModel by viewModels()
    private val groupAdapter by lazy { GroupAdapter<GroupieViewHolder>() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentHistoryBinding.inflate(inflater, container, false)

        // Set no data image
        binding.noData.noDataImage.setImageResource(R.drawable.img_empty_box_color_6)

        // Set up recyclerview
        binding.historyRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupAdapter
            itemAnimator = ScaleInBottomAnimator()
        }


        // Expenses sorted by month/year under expandable header
        viewModel.readAllExpense(viewModel.userEmail()).observe(viewLifecycleOwner) { allExpenses ->

            allExpenses.groupBy {
                Pair(it.createdOn.month, it.createdOn.year)
            }.toSortedMap { o1, o2 ->
                compareValuesBy(o1, o2, { it.first.value }, { it.second })
            }.toList().reversed().forEach { (monthYear, expenses) ->

                val currency = viewModel.getCurrency()
                val historyTotal = expenses.sumOf { it.amount.toDouble() }

                val monthTotal = if (currency.length == 1)
                    String.format(
                        resources.getString(R.string.monetary_amount),
                        historyTotal,
                        currency
                    )
                else
                    String.format(
                        resources.getString(R.string.monetary_amount_long),
                        historyTotal,
                        currency
                    )

                val now = LocalDate.now()
                val isExpanded =
                    now.monthValue == monthYear.first.value && now.year == monthYear.second

                val historyFieldComparator = Comparator<Expense> { o1, o2 ->
                    if (viewModel.getSortOrder() == "by_category") o1.ofCategory.compareTo(o2.ofCategory)
                    else o1.amount.compareTo(o2.amount)
                }

                viewModel.readAllCategoryIcon().observe(viewLifecycleOwner) { cIcon ->

                    val history = expenses.sortedWith(historyFieldComparator).map {
                        val icon = cIcon.find { c -> c.title == it.ofCategory }?.icon
                            ?: R.drawable.cat_other
                        History(it, currency, icon)
                    }

                    ExpandableGroup(
                        HistoryHeader(
                            monthYear,
                            resources.configuration.locales[0],
                            monthTotal
                        ), isExpanded
                    ).apply {
                        add(Section(history))
                        groupAdapter.add(this)
                    }

                }

            }

            ViewBehavior.getNoDataViewVisibility(
                allExpenses,
                binding.noData.noDataImage,
                binding.noData.noDataText
            )

        }

        return binding.root

    }

}