package de.lorenzgorse.coopmobile

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario

class TestMenuItem(private val itemId: Int) : MenuItem {

    override fun expandActionView(): Boolean {
        TODO("not implemented")
    }

    override fun hasSubMenu(): Boolean {
        TODO("not implemented")
    }

    override fun getMenuInfo(): ContextMenu.ContextMenuInfo {
        TODO("not implemented")
    }

    override fun getItemId(): Int = itemId

    override fun getAlphabeticShortcut(): Char {
        TODO("not implemented")
    }

    override fun setEnabled(enabled: Boolean): MenuItem {
        TODO("not implemented")
    }

    override fun setTitle(title: CharSequence?): MenuItem {
        TODO("not implemented")
    }

    override fun setTitle(title: Int): MenuItem {
        TODO("not implemented")
    }

    override fun setChecked(checked: Boolean): MenuItem {
        TODO("not implemented")
    }

    override fun getActionView(): View {
        TODO("not implemented")
    }

    override fun getTitle(): CharSequence {
        TODO("not implemented")
    }

    override fun getOrder(): Int {
        TODO("not implemented")
    }

    override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?): MenuItem {
        TODO("not implemented")
    }

    override fun getIntent(): Intent {
        TODO("not implemented")
    }

    override fun setVisible(visible: Boolean): MenuItem {
        TODO("not implemented")
    }

    override fun isEnabled(): Boolean {
        TODO("not implemented")
    }

    override fun isCheckable(): Boolean {
        TODO("not implemented")
    }

    override fun setShowAsAction(actionEnum: Int) {
        TODO("not implemented")
    }

    override fun getGroupId(): Int {
        TODO("not implemented")
    }

    override fun setActionProvider(actionProvider: ActionProvider?): MenuItem {
        TODO("not implemented")
    }

    override fun setTitleCondensed(title: CharSequence?): MenuItem {
        TODO("not implemented")
    }

    override fun getNumericShortcut(): Char {
        TODO("not implemented")
    }

    override fun isActionViewExpanded(): Boolean {
        TODO("not implemented")
    }

    override fun collapseActionView(): Boolean {
        TODO("not implemented")
    }

    override fun isVisible(): Boolean {
        TODO("not implemented")
    }

    override fun setNumericShortcut(numericChar: Char): MenuItem {
        TODO("not implemented")
    }

    override fun setActionView(view: View?): MenuItem {
        TODO("not implemented")
    }

    override fun setActionView(resId: Int): MenuItem {
        TODO("not implemented")
    }

    override fun setAlphabeticShortcut(alphaChar: Char): MenuItem {
        TODO("not implemented")
    }

    override fun setIcon(icon: Drawable?): MenuItem {
        TODO("not implemented")
    }

    override fun setIcon(iconRes: Int): MenuItem {
        TODO("not implemented")
    }

    override fun isChecked(): Boolean {
        TODO("not implemented")
    }

    override fun setIntent(intent: Intent?): MenuItem {
        TODO("not implemented")
    }

    override fun setShortcut(numericChar: Char, alphaChar: Char): MenuItem {
        TODO("not implemented")
    }

    override fun getIcon(): Drawable {
        TODO("not implemented")
    }

    override fun setShowAsActionFlags(actionEnum: Int): MenuItem {
        TODO("not implemented")
    }

    override fun setOnMenuItemClickListener(
        menuItemClickListener: MenuItem.OnMenuItemClickListener?
    ): MenuItem {
        TODO("not implemented")
    }

    override fun getActionProvider(): ActionProvider {
        TODO("not implemented")
    }

    override fun setCheckable(checkable: Boolean): MenuItem {
        TODO("not implemented")
    }

    override fun getSubMenu(): SubMenu {
        TODO("not implemented")
    }

    override fun getTitleCondensed(): CharSequence {
        TODO("not implemented")
    }

}

fun <F: Fragment> FragmentScenario<F>.selectMenuItem(itemId: Int) {
    this.onFragment { it.onOptionsItemSelected(TestMenuItem(itemId)) }
}
