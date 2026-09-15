#!/usr/bin/env python3
"""
Приводит datasource-ссылки в экспортированном JSON дашборда Grafana
к рабочему паттерну этого проекта: простая строка "Prometheus",
совпадающая с `name: Prometheus` в grafana/provisioning/datasources/datasource.yml
(тот же паттерн, что уже используется в grafana/dashboards/gatling-custom.json).

Убирает:
  - шаблонную переменную "${DS_PROMETHEUS}" (не подставилась при ручном импорте)
  - устаревший захардкоженный UID автора дашборда с grafana.com (например "PBFA97CFB590B2093")
  - объектную форму {"type": "prometheus", "uid": "..."} -> заменяется на "Prometheus"

Использование:
    python3 fix-dashboard-datasource.py <входной.json> [<выходной.json>]

Если выходной путь не указан - перезаписывает входной файл.
"""
import json
import re
import sys
from pathlib import Path

TARGET = "Prometheus"  # должно совпадать с `name:` в datasource.yml


def is_broken_datasource_value(value) -> bool:
    """True, если это ссылка на datasource, которую надо заменить."""
    if isinstance(value, str):
        return value.startswith("${DS_") or value == "" or re.fullmatch(r"[A-Za-z0-9_-]{8,}", value) and value != TARGET
    if isinstance(value, dict):
        uid = value.get("uid", "")
        return isinstance(uid, str) and (uid.startswith("${DS_") or uid != TARGET)
    return False


def fix_node(node):
    """Рекурсивно проходит по дереву JSON и чинит все datasource-поля."""
    if isinstance(node, dict):
        for key, value in list(node.items()):
            if key == "datasource":
                if isinstance(value, dict):
                    # объектная форма {"type": "prometheus", "uid": "..."}
                    if value.get("type") in (None, "prometheus", "datasource"):
                        node[key] = TARGET
                elif isinstance(value, str):
                    if value.startswith("${DS_") or value in (
                        "PBFA97CFB590B2093",  # типовой захардкоженный UID из экспортов grafana.com
                    ):
                        node[key] = TARGET
                    # уже "Prometheus" или другое валидное имя - не трогаем
            else:
                fix_node(value)
    elif isinstance(node, list):
        for item in node:
            fix_node(item)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    in_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else in_path

    data = json.loads(in_path.read_text(encoding="utf-8"))

    # Если файл целиком - обёртка вида {"dashboard": {...}, "meta": {...}} (формат экспорта
    # через Share > Export, или ответ API /api/dashboards/uid/...) - разворачиваем.
    dashboard = data.get("dashboard", data)

    # Убираем секцию __inputs (она как раз и создаёт переменную DS_PROMETHEUS
    # при импорте) - больше не нужна, раз мы фиксируем датасорс напрямую.
    dashboard.pop("__inputs", None)
    dashboard.pop("__requires", None)

    # Убираем "id" - при провижининге через файл id должен быть null,
    # иначе Grafana может конфликтовать с уже существующим дашбордом.
    dashboard["id"] = None

    fix_node(dashboard)

    out_path.write_text(
        json.dumps(dashboard, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Готово: {out_path}")
    print('Все datasource-ссылки приведены к строке "Prometheus".')


if __name__ == "__main__":
    main()
