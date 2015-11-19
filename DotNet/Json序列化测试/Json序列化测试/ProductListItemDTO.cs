using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 门票产品列表项信息
    /// </summary>
    [Serializable]
    public class ProductListItemDTO
    {
        /// <summary>
        /// 门票产品ID
        /// </summary>
        public int ID { get; set; }
        /// <summary>
        /// 门票产品名称
        /// </summary>
        public string Name { get; set; }
        /// <summary>
        /// 工作截止时间（当天在此时间前预订）
        /// </summary>
        public string WorkEndTime { get; set; }
        /// <summary>
        /// 收款方式(O-现付;P-预付)
        /// </summary>
        public string PayMode { get; set; }        

        /// <summary>
        /// 门票资源简单信息列表
        /// </summary>
        public List<ResourceListItemDTO> ResourceListItemList { get; set; }

        /// <summary>
        /// 门票资源市场价
        /// </summary>
        public int MarketPrice { get; set; }
        /// <summary>
        /// 门票资源携程卖价
        /// </summary>
        public int Price { get; set; }
        /// <summary>
        /// 是否返现
        /// </summary>
        public bool IsReturnCash { get; set; }
        /// <summary>
        /// 返现金额
        /// </summary>
        public int ReturnCashAmount { get; set; }

        /// <summary>
        /// 门票属性组
        /// 目前属性组包含有： 
        /// 1.  Key：SalesGroup 
        ///     Name：销售相关属性组
        ///     TicketAttributes：
        ///     a)  Key：MobilePreference
        ///         Name：手机专享金额
        ///         Value：对应优惠金额值    
        /// </summary>
        public List<TicketAttributeGroupDTO> TicketGroupAttributes { get; set; }

        /// <summary>
        /// 促销活动信息
        /// </summary>
        public List<ProductPromotionInfoDTO> PromotionInfoList { get; set; }

        /// <summary>
        /// 是否主产品
        /// </summary>
        public bool IsMaster { get; set; }

        /// <summary>
        /// 产品最早可预订时间
        /// </summary>
        public DateTime CanBookingFirstDate { get; set; }

        /// <summary>
        /// 提前预订天数，根据资源计算得出
        /// </summary>
        public int AdvanceBookingDays { get; set; }

        /// <summary>
        /// 提前预订时间，根据资源计算得出
        /// </summary>
        public string AdvanceBookingTime { get; set; }

        /// <summary>
        /// 产品类型ID
        /// </summary>
        public int CategoryID { get; set; }
    }
}
