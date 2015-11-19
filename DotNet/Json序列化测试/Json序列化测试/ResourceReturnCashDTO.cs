using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    [Serializable]
    public class ResourceReturnCashDTO
    {
        /// <summary>
        /// 资源ID
        /// </summary>
        public int ResourceID { get; set; }
        /// <summary>
        /// 返现金额
        /// </summary>
        public decimal ReturnCashAmount { get; set; }
        /// <summary>
        /// 手机立减金额
        /// </summary>
        public decimal MobilePreferenceAmount { get; set; }

    }
}
