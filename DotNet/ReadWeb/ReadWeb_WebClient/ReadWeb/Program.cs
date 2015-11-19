using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Net;
using System.IO;
using System.Windows.Forms;

namespace ReadWeb
{
    class Program
    {
        static void Main(string[] args)
        {
            string url = "http://sports.sina.com.cn/basketball/nba/2015-08-09/doc-ifxftvni8854953.shtml";  //从指定网站下载数据
            string result = null;

            try
            {
                WebClient client = new WebClient();
                client.Credentials = CredentialCache.DefaultCredentials;    //获取或设置用于向Internet资源请求进行身份验证的网络凭据
                Byte[] data = client.DownloadData(url);
                result = Encoding.UTF8.GetString(data);
                Console.WriteLine(result);

                using (StreamWriter sw = new StreamWriter(@"d:\Users\hejun\Desktop\Output.html"))
                {
                    sw.Write(result);
                }
                Console.WriteLine();
            }
            catch (Exception ex)
            {
                MessageBox.Show( ex.Message );
            }
        }
    }
}
