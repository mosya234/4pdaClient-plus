package org.softeg.slartus.forpdaplus.db

import android.content.Context
import com.readystatesoftware.sqliteasset.SQLiteAssetHelper
import org.softeg.slartus.forpdaplus.prefs.Preferences

/**
 * Created with IntelliJ IDEA.
 * User: slinkin
 * Date: 25.03.13
 * Time: 9:27
 * To change this template use File | Settings | File Templates.
 */
class ForumStructDbHelper(context: Context) : SQLiteAssetHelper(context, DATABASE_NAME, Preferences.System.getSystemDir(), null, DATABASE_VERSION) {

    init {
        setForcedUpgrade(DATABASE_VERSION)
    }

    companion object {

        private const val DATABASE_VERSION = 22
        private const val DATABASE_NAME = "forum_struct"
    }


}

