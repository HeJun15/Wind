using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    [Serializable]
    public class ProductPromotionInfoDTO
    {

        // Summary:
        //     优惠金额
        public decimal DeductionAmount { get; set; }
        //
        // Summary:
        //     立减类型(0-固定金额，1-百分比)
        public int DeductionType { get; set; }
        //
        // Summary:
        //     活动展示名称
        public string DisplayName { get; set; }
        //
        // Summary:
        //     促销编号
        public int PromotionID { get; set; }
    }
}
