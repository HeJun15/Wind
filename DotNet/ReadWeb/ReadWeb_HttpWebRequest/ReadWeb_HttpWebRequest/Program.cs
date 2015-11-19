using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Net;
using System.IO;
using System.Windows.Forms;

namespace ReadWeb_HttpWebRequest
{
    class Program
    {
        static void Main(string[] args)
        {
            string result = null;
            string url = "";
            WebResponse response = null;
            StreamReader reader = null;

            try
            {
                HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
                request.Method = "GET";
                response = request.GetResponse();
                reader = new StreamReader(response.GetResponseStream(),Encoding.UTF8);
                result = reader.ReadToEnd();
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
            finally
            {
                if (reader != null)
                    reader.Close();
                if (response != null)
                    response.Close();
            }
        }
    }
}
