package com.turni.calendario.export

/** Balance ordered columns without splitting an assignment or dropping any details. */
internal fun packPdfColumns(heights:List<Float>,columns:Int,available:Float):List<List<Int>>? {
    require(columns>0&&available>0f)
    require(heights.all{it.isFinite()&&it>=0f})
    if(heights.any{it>available})return null
    fun pack(limit:Float):List<List<Int>>? {
        val result=mutableListOf<MutableList<Int>>(mutableListOf())
        var used=0f
        heights.forEachIndexed{index,height->
            if(used+height>limit){if(result.size==columns)return null;result.add(mutableListOf());used=0f}
            result.last().add(index);used+=height
        }
        return result
    }
    val initial=pack(available)?:return null
    if(columns==1||heights.isEmpty())return initial
    var low=maxOf(heights.maxOrNull()?:0f,heights.sum()/columns)
    var high=available
    repeat(20){val middle=(low+high)/2f;if(pack(middle)!=null)high=middle else low=middle}
    return pack(high)?:initial
}
