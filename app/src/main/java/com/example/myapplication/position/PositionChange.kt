package com.example.myapplication.position

class PositionChange {
    companion object{
        const val VALUE_LENGTH = 5
        const val POSITION_LENGTH = 5
    }
    private var array = Array(POSITION_LENGTH){Array(VALUE_LENGTH){0.0f}}
    fun leftShiftArray(index: Int, shift: Int){
        for (i in 0 until shift){
            for (j in 0 until VALUE_LENGTH-1){
                this.array[index][j] = this.array[index][j+1]
            }
            this.array[index][VALUE_LENGTH-1] = 0.0f
        }
    }
    fun insertValue(index: Int, value: Float){
        var insertIndex = -1
        for (i in 0 until VALUE_LENGTH){
            if (this.array[index][i] == 0.0f){
                insertIndex = i
                break
            }
        }
        if(insertIndex == -1){
            this.leftShiftArray(index, 1)
            insertIndex = VALUE_LENGTH-1
        }
        this.array[index][insertIndex] = value
    }
    fun getArray() : Array<Array<Float>>{
        return this.array;
    }
    fun displayArray(){
        println("Displaying the array")
        for (i in 0 until POSITION_LENGTH){
            for (j in 0 until VALUE_LENGTH){
                print("${this.array[i][j]} ")
            }
            println()
        }
    }
    fun getChange(index: Int) : Float{
        var totalChange: Float = 0.0f;
        for (i in 0 until VALUE_LENGTH-1){
            totalChange += this.array[index][i+1] - this.array[index][i]
        }
        return totalChange
    }
    fun isOnlyOneChange(index: Int): Boolean{
        var lists: MutableList<Int> = mutableListOf<Int>(0,1,2,3,4)
        lists.remove(index)
        var check: Boolean = true;
        for(i in lists){
//            Log.d("민규", (array[index][VALUE_LENGTH-1] - array[i][VALUE_LENGTH-1]).toString())
            if(array[index][VALUE_LENGTH-1] - array[i][VALUE_LENGTH-1] < -0.05){
                check = false;
//                Log.d("민규", i.toString())
                break
            }
        }
        return check
    }
}