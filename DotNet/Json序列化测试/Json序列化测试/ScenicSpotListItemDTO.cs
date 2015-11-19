using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace JsonTest
{
    /// <summary>
    /// 景点列表项信息
    /// </summary>
    [Serializable]
    public class ScenicSpotListItemDTO
    {
        /// <summary>
        /// 景点ID
        /// </summary>
        public int ID;
        /// <summary>
        /// 景点名称
        /// </summary>
        public string Name;
        /// <summary>
        /// 景点星级
        /// </summary>
        public int Star { get; set; }
        /// <summary>
        /// 景点地址
        /// </summary>
        public string Address { get; set; }
        /// <summary>
        /// 景点所属景区ID
        /// </summary>
        public int DistrictID { get; set; }
        /// <summary>
        /// 景点所属景区名称
        /// </summary>
        public string DistrictName { get; set; }
        /// <summary>
        /// 景点所属省ID
        /// </summary>
        public int ProvinceID { get; set; }
        /// <summary>
        /// 景点所属省名称
        /// </summary>
        public string ProvinceName { get; set; }
        /// <summary>
        /// 景点所属国家ID
        /// </summary>
        public int CountryID { get; set; }
        /// <summary>
        /// 景点所属国家名称
        /// </summary>
        public string CountryName { get; set; }
        /// <summary>
        /// 景点活动
        /// </summary>
        public string Activity { get; set; }
        /// <summary>
        /// 产品经理推荐
        /// </summary>
        public string ProductManagerRecommand { get; set; }
        /// <summary>
        /// 点评分数（主产品）
        /// </summary>
        public float CommentGrade { get; set; }
        /// <summary>
        /// 点评人数（主产品）
        /// </summary>
        public int CommentUserCount { get; set; }
        /// <summary>
        /// 30天内订单数
        /// </summary>
        public int OrderCount { get; set; }
        /// <summary>
        /// 门票产品简单信息列表
        /// </summary>
        public List<ProductListItemDTO> ProductListItemList { get; set; }

        /// <summary>
        /// 此属性可能会被废弃，请勿使用(废弃)
        /// </summary>        
        public string CoverImageUrl { get; set; }

        /// <summary>
        /// 此属性可能会被废弃，请勿使用
        /// </summary>        
        public int CoverImageId { get; set; }

        /// <summary>
        /// 此属性可能会被废弃，请勿使用
        /// </summary>        
        public string CoverSmallImageUrl { get; set; }

        /// <summary>
        /// 此属性可能会被废弃，请勿使用
        /// </summary>        
        public string CoverImageBaseUrl { get; set; }

        /// <summary>
        /// 市场价
        /// </summary>
        public int MarketPrice { get; set; }
        /// <summary>
        /// 携程卖价
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
        ///     b)  Key: TodayEnter
        ///         Name: 标识是否当日可入园
        ///         Value：当日入园
        /// </summary>
        public List<TicketAttributeGroupDTO> TicketGroupAttributes { get; set; }

        /// <summary>
        /// 图片，目前取主产品第一张图片，若主产品无图数据，此字段也无数据
        /// </summary>
        public string Image { get; set; }

        /// <summary>
        /// 景点与传入经纬度的距离
        /// </summary>
        public decimal Distance { get; set; }

        /// <summary>
        /// 营销标签
        /// </summary>
        public string SaleTag { get; set; }

        /// <summary>
        /// 限购提示信息
        /// </summary>
        public string LimitSaleMsg { get; set; }

        /// <summary>
        /// 资源最优返现信息
        /// </summary>
        public ResourceReturnCashDTO ResourceReturnCash { get; set; }
        /// <summary>
        /// 景点对应的url 地址
        ///  </summary>
        public string Url { get; set; }
    }

}
