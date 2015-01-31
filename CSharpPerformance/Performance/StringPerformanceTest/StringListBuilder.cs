using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace Performance
{
	public class StringListBuilder
	{
		private List<string> _list;

		public StringListBuilder(int capacity)
		{
			this._list = new List<string>(capacity);
		}

		public StringListBuilder Append(string s)
		{
			this._list.Add(s);
			return this;
		}

		public string ToString()
		{
			return String.Concat(this._list.ToArray());
		}
	}
}
