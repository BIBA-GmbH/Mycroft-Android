/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package coala.ai.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coala.ai.activities.MainActivity
import coala.ai.R
import coala.ai.models.UtteranceFrom
import coala.ai.models.ButtonsData
import coala.ai.models.TableData
import coala.ai.models.Utterance
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.mycroft_buttons_layout.view.*
import kotlinx.android.synthetic.main.mycroft_image_layout.view.*
import kotlinx.android.synthetic.main.mycroft_table_layout.view.*
import kotlinx.android.synthetic.main.user_card_layout.view.*


/**
 * Created by paul on 2016/06/22.
 */
class MycroftAdapter(private val utteranceList: List<Utterance>, private val ctx: Context, private val menuInflater: MenuInflater) : RecyclerView.Adapter<MycroftAdapter.UtteranceViewHolder>() {

    var onLongClickListener: OnLongItemClickListener? = null
    var lastClickedPos : Int = -1

    init {
        setHasStableIds(true)
    }

    interface OnLongItemClickListener {
        fun itemLongClicked(v: View, position: Int)
    }

    fun setOnLongItemClickListener(listener: OnLongItemClickListener) {
        onLongClickListener = listener
    }

    override fun getItemCount(): Int {
        return utteranceList.size

    }

    override fun onBindViewHolder(utteranceViewHolder: UtteranceViewHolder, i: Int) {
        utteranceViewHolder.bind(utteranceList[i],i)

    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): UtteranceViewHolder {
        val itemView = when (i) {
            UtteranceFrom.MYCROFT.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.mycroft_card_layout, viewGroup, false)
            UtteranceFrom.USER.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.user_card_layout, viewGroup, false)
            UtteranceFrom.MYCROFT_IMG.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.mycroft_image_layout, viewGroup, false)
            UtteranceFrom.BUTTONS.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.mycroft_buttons_layout, viewGroup, false)
            UtteranceFrom.TABLE.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.mycroft_table_layout, viewGroup, false)
            UtteranceFrom.BUTTONS_CHAT.id -> LayoutInflater.from(viewGroup.context).inflate(R.layout.mycroft_buttons_layout, viewGroup, false)
            else -> throw IndexOutOfBoundsException("No such view id $i")
        }

        return UtteranceViewHolder(itemView, menuInflater, i)
    }

    override fun getItemViewType(position: Int): Int {
        val message = utteranceList[position]
        return message.from.id
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }


    inner class UtteranceViewHolder(v: View, private val menuInflater: MenuInflater, private val i: Int) : RecyclerView.ViewHolder(v), View.OnCreateContextMenuListener {
        val vUtterance = v.utterance
        val img = v.res_img
        val imgLink = v.imgLink
        val buttons_recycler_view = v.child_recycler_view
        val table_recycler_view = v.table_recyclerView

        init {
            v.setOnCreateContextMenuListener(this)
        }

        fun bind(result : Utterance , pos :Int){
            // show images
            if(itemViewType == UtteranceFrom.MYCROFT_IMG.id) {
                // Handles errors by showing the coala logo.
                Glide.with(ctx)
                    .load(result.utterance)
                    .apply(
                        RequestOptions()
                            .error(R.drawable.error)
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .listener(object : RequestListener<Drawable>{
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            Log.i("GlideExceptionloaded",e.toString())
                            imgLink.visibility = View.VISIBLE
                            imgLink.text = result.utterance

                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            Log.i("GlideExceptionloaded","loaded")
                            imgLink.visibility = View.INVISIBLE
                            return false
                        }


                    })

                    .into(img)


                // show list of buttons in chat
            } else if(itemViewType == UtteranceFrom.BUTTONS.id) {
                val gson = Gson()
                val arrayListType = object : TypeToken<ArrayList<ButtonsData>>() {}.type
                var buttonList: ArrayList<ButtonsData> = gson.fromJson(result.utterance, arrayListType)
                val childMembersAdapter = ChildButtonAdapter(ctx, buttonList)
                //childMembersAdapter.addData(buttonList)
                childMembersAdapter.setHasStableIds(true)
                buttons_recycler_view.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.VERTICAL, false)
                buttons_recycler_view.adapter = childMembersAdapter
                childMembersAdapter.setOnItemClickListener(object : ChildButtonAdapter.OnItemClickListener{

                    override fun itemClicked(position: Int, clickVal: Boolean) {
                        if (!clickVal){
                            childMembersAdapter.refreshList(position)
                            childMembersAdapter.notifyDataSetChanged()
                            lastClickedPos = position
                        }

                        if(lastClickedPos == position || lastClickedPos == -1){
                            (ctx as MainActivity).sendMessage(buttonList[position].payload, buttonList[position].title )
                        }

                    }

                })

            }

            // show table in chat
            else if(itemViewType == UtteranceFrom.TABLE.id) {
                val gson = Gson()
                val arrayListType = object : TypeToken<ArrayList<TableData>>() {}.type
                var tableList: ArrayList<TableData> = gson.fromJson(result.utterance, arrayListType)
                val tableDataAdapter = TableAdapter(ctx,tableList)
                table_recycler_view.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.VERTICAL, false)
                table_recycler_view.adapter = tableDataAdapter


            }

            // show button in chat history
            else if(itemViewType == UtteranceFrom.BUTTONS_CHAT.id) {
                val gson = Gson()
                val arrayListType = object : TypeToken<ArrayList<ButtonsData>>() {}.type
                val buttonList: ArrayList<ButtonsData> = gson.fromJson(utteranceList[i].utterance, arrayListType)
                val childMembersAdapter = ChildButtonAdapterChat(ctx)
                childMembersAdapter.addData(buttonList)
                val divider = DividerItemDecoration(
                    buttons_recycler_view.getContext(),
                    DividerItemDecoration.HORIZONTAL
                )

                ContextCompat.getDrawable(
                    ctx,
                    R.drawable.doted_line
                )?.let {
                    divider.setDrawable(
                        it
                    )
                }

                buttons_recycler_view.addItemDecoration(divider)
                buttons_recycler_view.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
                buttons_recycler_view.adapter = childMembersAdapter

            }



            // show text
            else{
                var textToDisplay = result.utterance
                // remove "/" present in payloads of button
                textToDisplay = textToDisplay.replace(Regex("^[/]+?"), "")
                // The Regex ensures that SSML tags are not visualized in the chat history.
                textToDisplay = textToDisplay.replace(Regex("(<{1}/?[^>]*>{1})"), "")
                vUtterance.text = textToDisplay

                itemView.setOnLongClickListener { v ->
                    onLongClickListener?.itemLongClicked(v, pos)
                    true
                }
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            when (i) {
                UtteranceFrom.USER.id -> menuInflater.inflate(R.menu.menu_user_utterance_context, menu)
                UtteranceFrom.MYCROFT.id -> menuInflater.inflate(R.menu.menu_mycroft_utterance_context, menu)
            }
        }
    }
}