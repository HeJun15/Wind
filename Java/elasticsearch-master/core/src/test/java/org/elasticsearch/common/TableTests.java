/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common;

import org.elasticsearch.test.ESTestCase;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;

public class TableTests extends ESTestCase {
    public void testFailOnStartRowWithoutHeader() {
        Table table = new Table();
        try {
            table.startRow();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("no headers added..."));
        }
    }

    public void testFailOnEndHeadersWithoutStart() {
        Table table = new Table();
        try {
            table.endHeaders();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("no headers added..."));
        }

    }

    public void testFailOnAddCellWithoutHeader() {
        Table table = new Table();
        try {
            table.addCell("error");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("no block started..."));
        }

    }

    public void testFailOnAddCellWithoutRow() {
        Table table = this.getTableWithHeaders();
        try {
            table.addCell("error");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("no block started..."));
        }

    }

    public void testFailOnEndRowWithoutStart() {
        Table table = this.getTableWithHeaders();
        try {
            table.endRow();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("no row started..."));
        }

    }

    public void testFailOnLessCellsThanDeclared() {
        Table table = this.getTableWithHeaders();
        table.startRow();
        table.addCell("foo");
        try {
            table.endRow(true);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("mismatch on number of cells 1 in a row compared to header 2"));
        }

    }

    public void testOnLessCellsThanDeclaredUnchecked() {
        Table table = this.getTableWithHeaders();
        table.startRow();
        table.addCell("foo");
        table.endRow(false);
    }

    public void testFailOnMoreCellsThanDeclared() {
        Table table = this.getTableWithHeaders();
        table.startRow();
        table.addCell("foo");
        table.addCell("bar");
        try {
            table.addCell("foobar");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("can't add more cells to a row than the header"));
        }

    }

    public void testSimple() {
        Table table = this.getTableWithHeaders();
        table.startRow();
        table.addCell("foo1");
        table.addCell("bar1");
        table.endRow();
        table.startRow();
        table.addCell("foo2");
        table.addCell("bar2");
        table.endRow();

        // Check headers
        List<Table.Cell> headers = table.getHeaders();
        assertEquals(2, headers.size());
        assertEquals("foo", headers.get(0).value.toString());
        assertEquals(2, headers.get(0).attr.size());
        assertEquals("f", headers.get(0).attr.get("alias"));
        assertEquals("foo", headers.get(0).attr.get("desc"));
        assertEquals("bar", headers.get(1).value.toString());
        assertEquals(2, headers.get(1).attr.size());
        assertEquals("b", headers.get(1).attr.get("alias"));
        assertEquals("bar", headers.get(1).attr.get("desc"));

        // Check rows
        List<List<Table.Cell>> rows = table.getRows();
        assertEquals(2, rows.size());
        List<Table.Cell> row = rows.get(0);
        assertEquals("foo1", row.get(0).value.toString());
        assertEquals("bar1", row.get(1).value.toString());
        row = rows.get(1);
        assertEquals("foo2", row.get(0).value.toString());
        assertEquals("bar2", row.get(1).value.toString());

        // Check getAsMap
        Map<String, List<Table.Cell>> map = table.getAsMap();
        assertEquals(2, map.size());
        row = map.get("foo");
        assertEquals("foo1", row.get(0).value.toString());
        assertEquals("foo2", row.get(1).value.toString());
        row = map.get("bar");
        assertEquals("bar1", row.get(0).value.toString());
        assertEquals("bar2", row.get(1).value.toString());

        // Check getHeaderMap
        Map<String, Table.Cell> headerMap = table.getHeaderMap();
        assertEquals(2, headerMap.size());
        Table.Cell cell = headerMap.get("foo");
        assertEquals("foo", cell.value.toString());
        cell = headerMap.get("bar");
        assertEquals("bar", cell.value.toString());

        // Check findHeaderByName
        cell = table.findHeaderByName("foo");
        assertEquals("foo", cell.value.toString());
        cell = table.findHeaderByName("missing");
        assertNull(cell);
    }

    private Table getTableWithHeaders() {
        Table table = new Table();
        table.startHeaders();
        table.addCell("foo", "alias:f;desc:foo");
        table.addCell("bar", "alias:b;desc:bar");
        table.endHeaders();
        return table;
    }
}
