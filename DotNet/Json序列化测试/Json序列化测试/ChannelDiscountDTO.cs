using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 渠道返现对象
    /// </summary>
    [Serializable]
    public class ChannelDiscountDTO
    {
        /// <summary>
        /// ChannelDiscountId
        /// </summary>
        public int ChannelDiscountId { get; set; }

        /// <summary>
        /// OptionId
        /// </summary>
        public long OptionId { get; set; }

        /// <summary>
        /// DistributionChannelId
        /// </summary>
        public int DistributionChannelId { get; set; }

        /// <summary>
        /// 返现金额
        /// </summary>
        public int CashBackAmount { get; set; }

        /// <summary>
        /// 渠道专享立减金额
        /// </summary>
        public int Discount { get; set; }


    }
}
