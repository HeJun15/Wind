using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Data;
using NPOI.SS.UserModel;
using NPOI.XSSF.UserModel;
using NPOI.HSSF.UserModel;
using System.IO;
using System.Diagnostics;
using System.Text.RegularExpressions;

namespace Manipulate_CSV
{
    class Program
    {
        //static DataTable GenerateData()
        //{
        //    DataTable data = new DataTable();
        //    for (int i = 0; i < 5; ++i)
        //    {
        //        data.Columns.Add("Columns_" + i.ToString(), typeof(string));
        //    }

        //    for (int i = 0; i < 10; ++i)
        //    {
        //        DataRow row = data.NewRow();
        //        row["Columns_0"] = "item0_" + i.ToString();
        //        row["Columns_1"] = "item1_" + i.ToString();
        //        row["Columns_2"] = "item2_" + i.ToString();
        //        row["Columns_3"] = "item3_" + i.ToString();
        //        row["Columns_4"] = "item4_" + i.ToString();
        //        data.Rows.Add(row);
        //    }
        //    return data;
        //}

        static void PrintData(DataTable data)
        {
            if (data == null) return;
            for (int i = 0; i < data.Rows.Count; ++i)
            {
                for (int j = 0; j < data.Columns.Count; ++j)
                    Console.Write("{0} ", data.Rows[i][j]);
                Console.Write("\n");
            }
            Console.Write("\n");
            Console.Write("\n");
            Console.Write("\n");
        }

        static void TestExcelWrite(DataTable data, string fileName, string sheetName)
        {
            try
            {
                using (ExcelHelper excelHelper = new ExcelHelper(fileName))
                {
                    int count = excelHelper.DataTableToExcel(data, sheetName, true);
                    if (count > 0)
                        Console.WriteLine("Number of imported data is {0} ", count);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception: " + ex.Message);
            }
        }

        static DataTable TestExcelRead(string file, string sheetName)
        {
            DataTable dt = new DataTable();
            try
            {
                using (ExcelHelper excelHelper = new ExcelHelper(file))
                    dt = excelHelper.ExcelToDataTable(sheetName, true);
                return dt;
            }
            catch (Exception ex)
            {
                Console.WriteLine("Exception: " + ex.Message);
                return null;
            }
        }


        static void Main(string[] args)
        {
            string projectdllfile = @"d:\Users\hejun\Desktop\ProjectOnlineDLL.xlsx";           // 不识别Project_Product_DLL.xlsx命名
            string dllfile = @"d:\Users\hejun\Desktop\TotalLibrary.xlsx";
            string projectfile = @"d:\Users\hejun\Desktop\Project.xlsx";
            // TestExcelWrite(file);
            DataTable projectdlldata = TestExcelRead(projectdllfile, "MySheet");                // 获取项目DLL的表格
            DataTable dlldata = TestExcelRead(dllfile, "MySheet");                              // 获取仓库DLL的表格
            DataTable projectdata = TestExcelRead(projectfile, "MySheet");                      // 获取项目的表格
            DataTable newdata = new DataTable();                                                // 新建储存ProjectDLL的表格
            newdata.Columns.Add("ProjectID", typeof(string));
            newdata.Columns.Add("DllID", typeof(string));


            for (int i = 0; i < projectdlldata.Rows.Count; ++i)
            {
                DataRow newrow = newdata.NewRow();
                DataRow projectdllrow = projectdlldata.Rows[i];

                for (int j = 0; j < projectdlldata.Columns.Count; ++j)
                {
                    if (projectdllrow[j] != null && j == 2)
                    {
                        string temp = projectdllrow[j].ToString();
                        if (temp != "NULL")
                        {
                            string regexStr = "Library.*";
                            Regex reg = new Regex(regexStr);
                            Match mc = reg.Match(temp);

                            temp = mc.Value;
                            for (int k = 0; k < dlldata.Rows.Count; ++k)
                            {
                                DataRow dllrow = dlldata.Rows[k];
                                string temp1 = dllrow[5].ToString();
                                if(temp == temp1)
                                {
                                    newrow["DllID"] = dllrow[0].ToString();
                                    break;
                                }
                                else
                                    newrow["DllID"] = "NULL";
                            }
                        }
                        else
                            newrow["DllID"] = "NULL";
                    }

                    if (projectdllrow[j] != null && j == 3)
                    {
                        string temp = projectdllrow[j].ToString();
                        if (temp != "NULL")
                        {
                            string regexStr = @"Online.*(?=\\bin)";
                            Regex reg = new Regex(regexStr);
                            Match mc = reg.Match(temp);

                            temp = mc.Value;
                            for (int k = 0; k < projectdata.Rows.Count; ++k)
                            {
                                DataRow projectrow = projectdata.Rows[k];
                                string temp1 = projectrow[2].ToString();
                                if (temp == temp1)
                                {
                                    newrow["ProjectID"] = projectrow[0].ToString();
                                    break;
                                }
                                else
                                    newrow["ProjectID"] = "NULL";
                            }
                        }
                        else
                            newrow["ProjectID"] = "NULL";
                    }
                }
                
                newdata.Rows.Add(newrow);
            }
            //PrintData(projectdlldata);
            //PrintData(dlldata);
            //PrintData(projectdata);
            Console.WriteLine(newdata.Rows.Count);
            PrintData(newdata);

            string newfile = @"d:\Users\hejun\Desktop\newOnlineDll.xlsx";
            TestExcelWrite(newdata, newfile, "newSheet");

        }
    }
}
