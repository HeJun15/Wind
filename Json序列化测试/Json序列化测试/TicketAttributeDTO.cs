using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 门票属性DTO
    /// </summary>
    [Serializable]
    public class TicketAttributeDTO
    {
        /// <summary>
        /// 属性Key
        /// </summary>
        public string Key;

        /// <summary>
        /// 名称
        /// </summary>
        public string Name;

        /// <summary>
        /// 属性值
        /// </summary>
        public string Value { get; set; }
    }
}
