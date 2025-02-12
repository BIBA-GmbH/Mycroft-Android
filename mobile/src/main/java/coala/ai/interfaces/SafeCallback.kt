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

package coala.ai.interfaces

import java.util.concurrent.Callable

/**
 * Inversion of the [java.util.concurrent.Callable] interface.
 *
 *
 * Note that the [.call] method in this class is
 * not allowed to throw exceptions.
 *
 *
 * @author Philip Cohn-Cort
 */
interface SafeCallback<T> {
    /**
     * Variant of [Callable.call]
     * @param param any value. May be null.
     */
    fun call(param: T)
}
