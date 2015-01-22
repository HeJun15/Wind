using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 门票资源列表项信息
    /// </summary>
    [Serializable]
    public class ResourceListItemDTO
    {
        /// <summary>
        /// 门票资源ID
        /// </summary>
        public int ID { get; set; }
        /// <summary>
        /// 门票资源名称
        /// </summary>
        public string Name { get; set; }
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
        /// 适用人群(成人:1 儿童:2 学生:4 老人:8 其他:16 家庭:32)
        /// </summary>
        public int PeopleGroup { get; set; }

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
        /// 票资源渠道
        /// </summary>
        //public TicketDistributionChannelDTO TicketDistributionChannel { get; set; }

        /// <summary>
        /// 门票资源类型，值可能有：0，其他。1，特惠。2.单票。4.套票
        /// </summary>
        public int TicketType { get; set; }

        /// <summary>
        /// 是否高风险
        /// </summary>
        public bool IsHighRisk { get; set; }

        /// <summary>
        /// 兑换方式，值可能有：0，有效证件。1，确认单。2，短信。3，二维码。4，实物票，5，陪同单
        /// </summary>
        public string ExchangeMode { get; set; }

        /// <summary>
        /// 支付方式(O-现付;P-预付)
        /// </summary>
        public string PayMode { get; set; }

        /// <summary>
        /// 类型ID
        /// </summary>
        public int CategoryID { get; set; }

        /// <summary>
        /// 单位人数（人/份）
        /// </summary>
        public int UnitQuantity { get; set; }

        /// <summary>
        /// 营销标签
        /// </summary>
        public string SaleTag { get; set; }

        /// <summary>
        /// 限购提示信息
        /// </summary>
        public string LimitSaleMsg { get; set; }

        /// <summary>
        /// 是否免费
        /// </summary>
        public bool IsPriceFree { get; set; }

        /// <summary>
        /// 退订类型
        /// </summary>
        public int RefundType { get; set; }

        /// <summary>
        /// 是否限购
        /// </summary>
        public bool IsBookingLimit { get; set; }
        /// <summary>
        /// 渠道返现信息
        /// </summary>
        public List<ChannelDiscountDTO> ChannelDiscountList { get; set; }

        /// <summary>
        /// 是否特惠票
        /// </summary>
        public bool IsPreferential { get; set; }

        /// <summary>
        /// 是否单独售卖
        /// </summary>
        public bool IsSaleAlone { get; set; }
    }
}
