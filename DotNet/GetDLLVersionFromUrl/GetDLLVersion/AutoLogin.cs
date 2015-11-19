using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Net;
using System.IO;
using System.Windows.Forms;

namespace GetDLLVersionFromUrl
{
    public class AutoLogin
    {
        public String loginUrl { get; set; }
        public String loginName { get; set; }
        public String loginPassword { get; set; }

        public String Login()
        {
            HttpWebResponse response = null;
            StreamReader sr = null;
            string text = null;

            try
            {
                CookieContainer cc = new CookieContainer();
                string postData = "user=" + loginName + "&pass=" + loginPassword;
                byte[] byteArray = Encoding.UTF8.GetBytes(postData);

                HttpWebRequest webRequest = (HttpWebRequest)WebRequest.Create(new Uri(loginUrl));
                webRequest.CookieContainer = cc;
                webRequest.Method = "POST";
                webRequest.ContentType = "application/octet-stream";
                webRequest.ContentLength = byteArray.Length;
                Stream newStream = webRequest.GetRequestStream();
                newStream.Write(byteArray, 0, byteArray.Length);
                newStream.Close();

                response = (HttpWebResponse)webRequest.GetResponse();
                sr = new StreamReader(response.GetResponseStream(), Encoding.UTF8);
                text = sr.ReadToEnd();

                return text;
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message);
                return text;
            }
            finally
            {
                if (sr != null)
                    sr.Close();
                if (response != null)
                    response.Close();
            }
        }
    }
}
