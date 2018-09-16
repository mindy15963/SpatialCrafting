package fudge.spatialcrafting.common.tile.util

import java.util.*
import kotlin.collections.ArrayList

open class Arr3D<T>(private val height: Int, private val length: Int, private val width: Int, init: (i: Int, j: Int, k: Int) -> T) : Collection<T> {
    override operator fun contains(element: T): Boolean {
        forEach { if (it != element) return false }
        return true
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        elements.forEach { element -> if (!this.contains(element)) return false }
        return true
    }

    override fun isEmpty(): Boolean {
        forEach { if (it != null) return false }
        return true
    }

    override fun iterator(): Iterator<T>  = Iter(this)


    override val size: Int
        get() = height * length * width


    protected val wrappedArray = ArrayList<ArrayList<ArrayList<T>>>(height)

    init {
        for (heightIndex in 0 until height) {
            val arr2D = ArrayList<ArrayList<T>>(length)
            wrappedArray.add(arr2D)
            for (row in 0 until length) {
                val arr1D = ArrayList<T>(length)
                arr2D.add(arr1D)
                for (col in 0 until width) {
                    arr1D.add(init(heightIndex, row, col))
                }
            }
        }

    }


    operator fun get(height: Int, row: Int, col: Int): T  = wrappedArray[height][row][col]

    private operator fun get(coords: ArrCoords): T  = get(coords.height, coords.row, coords.col)


    operator fun set(height: Int, row: Int, col: Int, value: T) {
        wrappedArray[height][row][col] = value
    }

    fun indexedForEach(action: (i:Int,j:Int,k:Int,innerElement: T) -> Unit) {
        for(i in 0 until height){
            for(j in 0 until length){
                for(k in 0 until width){
                    action(i,j,k,get(i,j,k))
                }
            }
        }
    }

    fun inForEach(height: Int, action: (innerElement: T) -> Unit) {
        wrappedArray[height].forEach { arr1D -> arr1D.forEach(action) }
    }

    fun firstElement() : T = get(0,0,0)

    fun lastElement(): T = get(height - 1, length - 1, width  - 1)

    override fun equals(other: Any?): Boolean {
        if (other !is Arr3D<*>) return false
        if (other.height != this.height || other.length != this.length || other.width != this.width) return false

        for (i in other.indices) {
            for (j in other.indices) {
                for (k in other.indices) {

                    if (this[i, j, k] != other[i, j, k]) return false

                }
            }
        }
        return true

    }


    private fun areaIsNull(areaOfArr: Array<*>): Boolean {
        for (element in areaOfArr) {
            if (element is Array<*>) {
                for (innerElement in element) {
                    if (innerElement != null) return false
                }
            } else {
                if (element != null) return false
            }
        }

        return true
    }


    private inline fun <T1, T2> validate2DBounds(i: Int, arr1: Arr3D<T1>, arr2: Arr3D<T2>, continuer: () -> Unit, returner: (() -> Boolean)) {
        if (i >= arr1.height) {
            if (areaIsNull(arr2.wrappedArray[i].toArray())) continuer()  // This is fine, this area counts as equal
            else returner() // Out of bounds and in the other exists something that is not null, then it does not count as equal.
        }
    }

    private inline fun <T1, T2> validate1DBounds(i: Int, j: Int, arr1: Arr3D<T1>, arr2: Arr3D<T2>, continuer: () -> Unit, returner: (() -> Boolean)) {
        if (j >= arr1.length) {
            if (areaIsNull(arr2.wrappedArray[i][j].toArray())) continuer()  // This is fine, this area counts as equal
            else returner() // Out of bounds and in the other exists something that is not null, then it does not count as equal.
        }
    }

    private inline fun <T1, T2> validateBounds(i: Int, j: Int, k: Int, arr1: Arr3D<T1>, arr2: Arr3D<T2>, continuer: () -> Unit, returner: (() -> Boolean)) {
        if (k >= arr1.width) {
            if (arr2[i, j, k] == null) continuer()
            else returner()
        }
    }

    /**
     * Returns true if every inner element in the arrays is in the same in every position.
     * Counts null the same as the array being not in bounds, allowing for different sizes of arrays to be equal.
     *
     * @param tester     The condition in which two inner objects are considered equal
     * @param nullObject An object that should count as equal to null.
     */
    @JvmOverloads
    fun <T2> equalsDifSize(other: Arr3D<T2>, tester: (element1: T, element2: T2) -> Boolean = { e1, e2 -> e1 == e2 }): Boolean {

        (0..(Math.max(this.height, other.height) - 1)).forEach heightLoop@{ i ->

            // Out of bounds handling
            validate2DBounds(i, this, other, { return@heightLoop }, { return false })
            validate2DBounds(i, other, this, { return@heightLoop }, { return false })

            (0..(Math.max(this.length, other.length) - 1)).forEach rowLoop@{ j ->

                validate1DBounds(i, j, this, other, { return@rowLoop }, { return false })
                validate1DBounds(i, j, other, this, { return@rowLoop }, { return false })

                (0..(Math.max(this.width, other.width) - 1)).forEach colLoop@{ k ->

                    validateBounds(i, j, k, this, other, { return@colLoop }, { return false })
                    validateBounds(i, j, k, other, this, { return@colLoop }, { return false })


                    val thisElement = this[i, j, k]
                    val otherElement = other[i, j, k]

                    // Makes the tester null-safe
                    if (thisElement == null && otherElement == null) return@colLoop
                    if (thisElement == null || otherElement == null) return false

                    // Finally the test
                    if (!tester(thisElement, otherElement)) return false

                }

            }
        }

        return true
    }

    fun toList(): ArrayList<T> {
        val list = ArrayList<T>(size)
        forEach { list.add(it) }
        return list
    }

    override fun hashCode(): Int = Objects.hash(this.toList().toArray())

    private class Iter<T>(val arr: Arr3D<T>) : Iterator<T> {
        private var cursor = ArrCoords(0, 0, -1)       // index of next element to return

        override fun hasNext(): Boolean {
            return (cursor.height + 1) * (cursor.row + 1) * (cursor.col + 1) != arr.size
        }

        override fun next(): T {
            cursor.col++
            if (cursor.col == arr.width) {
                cursor.col = 0
                cursor.row++
            }
            if (cursor.row == arr.length) {
                cursor.row = 0
                cursor.height++
            }

            return arr[cursor]
        }

    }

    private class ArrCoords(var height: Int, var row: Int, var col: Int)


}