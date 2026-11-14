/*  Copyright (C) 2024 Arjan Schrijver

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint
import nodomain.freeyourgadget.gadgetbridge.R

@AndroidEntryPoint
class WelcomeFragmentIntro : Fragment() {
    private val viewModel: WelcomeIntroViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_welcome_intro, container, false)
        val uiState = viewModel.uiState.value
        val themes = uiState.themes

        val themeMenu = view.findViewById<MaterialAutoCompleteTextView>(R.id.app_theme_dropdown_menu)
        themeMenu.isSaveEnabled = false // https://github.com/material-components/material-components-android/issues/1464#issuecomment-1258051448
        themeMenu.setSimpleItems(themes.map { getString(it.labelRes) }.toTypedArray())
        themeMenu.setText(getString(uiState.selectedTheme.labelRes), false)
        themeMenu.setOnItemClickListener { _, _, position, _ ->
            viewModel.onThemeSelected(themes[position])
        }
        return view
    }
}
