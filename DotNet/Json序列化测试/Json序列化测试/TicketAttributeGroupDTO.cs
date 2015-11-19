using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 门票分组属性DTO
    /// </summary>
    [Serializable]
    public class TicketAttributeGroupDTO
    {
        /// <summary>
        /// 组属性Key
        /// </summary>
        public string Key;

        /// <summary>
        /// 名称
        /// </summary>
        public string Name;

        /// <summary>
        /// 门票属性集合
        /// </summary>
        public List<TicketAttributeDTO> TicketAttributes { get; set; }

    }
}
