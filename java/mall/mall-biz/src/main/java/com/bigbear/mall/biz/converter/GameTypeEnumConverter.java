package com.bigbear.mall.biz.converter;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.converters.ReadConverterContext;
import cn.idev.excel.converters.WriteConverterContext;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;

import com.bigbear.mall.api.common.enums.GamesEnum;

/**
 * 游戏类型枚举转换器 (FastExcel)
 * Excel 显示：原神/尘白禁区
 * Java 存储：0/1 (int)
 */
public class GameTypeEnumConverter implements Converter<Integer> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Integer.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    /**
     * 读：Excel 单元格 → Java int
     * Excel 存储 "原神" → Java 0
     * Excel 存储 "尘白禁区" → Java 1
     */
    @Override
    public Integer convertToJavaData(ReadConverterContext<?> context) {
        String cellValue = context.getReadCellData().getStringValue();

        if (cellValue == null || cellValue.trim().isEmpty()) {
            return null;
        }

        // 根据描述查找对应的 value
        for (GamesEnum game : GamesEnum.values()) {
            if (game.getLabel().equals(cellValue.trim())) {
                return Integer.parseInt(game.getValue());
            }
        }

        // 如果直接是数字字符串，直接解析
        try {
            return Integer.parseInt(cellValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 写：Java int → Excel 单元格
     * Java 0 → Excel "原神"
     * Java 1 → Excel "尘白禁区"
     */
    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<Integer> context) {
        Integer value = context.getValue();

        if (value == null) {
            return new WriteCellData<>("");
        }

        // 根据 int value 查找对应的 label
        for (GamesEnum game : GamesEnum.values()) {
            if (Integer.parseInt(game.getValue()) == value) {
                return new WriteCellData<>(game.getLabel());
            }
        }

        return new WriteCellData<>(String.valueOf(value));
    }
}
