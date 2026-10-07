import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Collapse, Empty, Input, List, Select, Space, Spin, Tag, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { calculateHardwarePrice, fetchHardwareCatalog } from './api/doorConfigurations'
import type { HardwareSelectionDto } from './api/types'
import { ATTRIBUTE_TAG_COLORS, withHardware, type CartItem } from './cart'
import { formatMoney } from './format'

// Минимальная высота блока характеристик двери в карточке (px): модель с тегами, цвет, размеры и количество;
// разделитель перед фурнитурой лежит примерно на одном уровне у всех карточек ленты.
const CONFIG_BLOCK_MIN_HEIGHT = 150

// Код прайс-листа фурнитуры сервиса «Фурнитура» (см. services.ts, change add-hardware-service).
const HARDWARE_PRICE_LIST_CODE = 'PL-003'

// Цветовой вариант фурнитуры (строка файла): артикул, цвет, цены.
interface HardwareOptionRow {
  optionId: number
  article: string
  colour: string
  retailPrice: number
  dealerPrice: number
}

// Модель (тип) фурнитуры прайс-листа с её цветовыми вариантами; brand — бренд листа файла.
interface HardwareModel {
  key: string
  brand: string
  category: string
  name: string
  options: HardwareOptionRow[]
}

// Любой вариант фурнитуры каталога целиком (в т.ч. из других прайс-листов) — для названий в списке фурнитуры
// двери; own — вариант принадлежит прайс-листу сервиса и редактируется здесь.
interface KnownOption {
  name: string
  article: string
  colour: string
  own: boolean
}

interface HardwareServiceScreenProps {
  items: CartItem[]
  onUpdateItem: (item: CartItem) => void
  onGoToConfigurator: () => void
}

// Экран сервиса «Фурнитура» (см. change add-hardware-service, hardware-service-ui): сверху горизонтально —
// двери из корзины (выбранная — цель добавления, с её фурнитурой), под ними — каталог прайс-листа: сначала
// бренд, затем модель, затем цвет. Добавление/изменение/удаление пересчитывает фурнитурную часть позиции
// стандартным расчётом фурнитуры (см. cart.ts, withHardware); корзину экран не хранит — только вызывает
// onUpdateItem. Фурнитура других прайс-листов (из конфигуратора двери) показывается, но не меняется и не удаляется.
function HardwareServiceScreen({ items, onUpdateItem, onGoToConfigurator }: HardwareServiceScreenProps) {
  const [models, setModels] = useState<HardwareModel[]>([])
  const [known, setKnown] = useState<Map<number, KnownOption>>(new Map())
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [brand, setBrand] = useState<string | undefined>()
  const [category, setCategory] = useState<string | undefined>()
  const [colourFilter, setColourFilter] = useState<string | undefined>()
  const [search, setSearch] = useState('')
  // Подсвеченная запись списка (последняя выбранная или добавленная).
  const [recordId, setRecordId] = useState<number | undefined>()
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchHardwareCatalog()
      .then((catalog) => {
        if (cancelled) {
          return
        }
        const result: HardwareModel[] = []
        const knownOptions = new Map<number, KnownOption>()
        for (const group of catalog) {
          for (const type of group.types) {
            const own = type.priceList.code === HARDWARE_PRICE_LIST_CODE
            for (const option of type.options) {
              knownOptions.set(option.id, { name: type.type.name, article: option.article ?? '', colour: option.colourName, own })
            }
            if (!own) {
              continue
            }
            result.push({
              key: `${type.type.id}`,
              brand: type.brand ?? '',
              category: group.category.name,
              name: type.type.name,
              options: type.options.map((option) => ({
                optionId: option.id,
                article: option.article ?? '',
                colour: option.colourName,
                retailPrice: option.retailPrice,
                dealerPrice: option.dealerPrice,
              })),
            })
          }
        }
        setModels(result)
        setKnown(knownOptions)
        // Первым выбирается первый бренд каталога — выбор бренда начинает подбор.
        setBrand((current) => current ?? Array.from(new Set(result.map((model) => model.brand))).sort()[0])
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : 'Не удалось загрузить каталог фурнитуры')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Цель добавления: выбранная дверь, пока она есть в корзине, иначе — первая.
  const selectedItem = items.find((item) => item.id === selectedItemId) ?? items[0] ?? null

  const brands = useMemo(() => Array.from(new Set(models.map((model) => model.brand))).sort(), [models])
  const categories = useMemo(
    () => Array.from(new Set(models.filter((model) => model.brand === brand).map((model) => model.category))),
    [models, brand],
  )
  // Цвета выбранного бренда и элемента — варианты фильтра «Цвет».
  const colours = useMemo(
    () =>
      Array.from(
        new Set(
          models
            .filter((model) => model.brand === brand && (!category || model.category === category))
            .flatMap((model) => model.options.map((option) => option.colour)),
        ),
      ).sort(),
    [models, brand, category],
  )

  // Записи каталога выбранного бренда: одна запись — один цветовой вариант модели.
  const records = useMemo(() => {
    const query = search.trim().toLowerCase()
    return models
      .filter((model) => model.brand === brand && (!category || model.category === category))
      .flatMap((model) => model.options.map((option) => ({ ...option, modelName: model.name })))
      .filter((record) => !colourFilter || record.colour === colourFilter)
      .filter(
        (record) =>
          !query ||
          record.modelName.toLowerCase().includes(query) ||
          record.colour.toLowerCase().includes(query) ||
          record.article.toLowerCase().includes(query),
      )
  }, [models, brand, category, colourFilter, search])

  // Применяет новый полный список фурнитуры к двери: считает фурнитуру стандартным расчётом и обновляет позицию
  // корзины; при ошибке корзина не меняется.
  function applySelections(item: CartItem, selections: HardwareSelectionDto[]) {
    setBusy(true)
    setActionError(null)
    const priced =
      selections.length === 0
        ? Promise.resolve({ totalRetailPrice: 0, totalDealerPrice: 0, hardware: [] })
        : calculateHardwarePrice({ hardware: selections })
    priced
      .then((response) => onUpdateItem(withHardware(item, selections, response)))
      .catch((error: unknown) => {
        setActionError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость фурнитуры')
      })
      .finally(() => setBusy(false))
  }

  function addHardware(optionId: number) {
    if (!selectedItem) {
      return
    }
    const current = selectedItem.exportRequest.hardware ?? []
    const existing = current.find((line) => line.hardwareOptionId === optionId)
    const next = existing
      ? current.map((line) =>
          line.hardwareOptionId === optionId ? { ...line, quantity: (line.quantity ?? 1) + 1 } : line,
        )
      : [...current, { hardwareOptionId: optionId, quantity: 1 }]
    applySelections(selectedItem, next)
  }

  function removeHardware(item: CartItem, optionId: number) {
    applySelections(
      item,
      (item.exportRequest.hardware ?? []).filter((line) => line.hardwareOptionId !== optionId),
    )
  }

  if (items.length === 0) {
    return (
      <div style={{ flex: 1 }}>
        <Empty description="Добавьте двери в корзину, чтобы подобрать для них фурнитуру">
          <Button type="primary" onClick={onGoToConfigurator}>
            Перейти в конфигуратор
          </Button>
        </Empty>
      </div>
    )
  }

  const doorsPanelContent = (
    <>
        {/* Двери — горизонтальная лента карточек над каталогом; при нехватке места прокручивается вбок. */}
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 4, alignItems: 'stretch' }}>
          {items.map((item) => {
            const selected = item.id === selectedItem?.id
            // Показывается только фурнитура из файла (прайс-лист сервиса); фурнитура, выбранная вне файла
            // (в конфигураторе двери), здесь не выводится, но остаётся в позиции и в цене.
            const hardware = (item.exportRequest.hardware ?? []).filter((line) => known.get(line.hardwareOptionId)?.own === true)
            const colour = item.detailRows[0]?.colour
            return (
              <Card
                key={item.id}
                size="small"
                hoverable
                onClick={() => setSelectedItemId(item.id)}
                style={{
                  flex: '0 0 300px',
                  cursor: 'pointer',
                  borderColor: selected ? '#1677ff' : undefined,
                  background: selected ? '#e6f4ff' : undefined,
                }}
              >
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  {/* Каждая характеристика двери — с новой строки: модель, теги, цвет, размеры, количество. */}
                  {/* Блок характеристик двери — фиксированной минимальной высоты, чтобы разделитель перед фурнитурой
                      был на одном уровне во всех карточках независимо от числа тегов и строк. */}
                  <div style={{ minHeight: CONFIG_BLOCK_MIN_HEIGHT, display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {/* Теги — сразу после названия модели, как в таблице корзины (те же цвета). */}
                  <div>
                    <Typography.Text type="secondary">Модель: </Typography.Text>
                    <Typography.Text strong style={{ marginInlineEnd: 6 }}>
                      {item.displayName}
                    </Typography.Text>
                    {(item.attributeTags ?? []).map((tag) => (
                      <Tag key={tag} color={ATTRIBUTE_TAG_COLORS[tag] ?? 'default'} style={{ marginInlineEnd: 4 }}>
                        {tag}
                      </Tag>
                    ))}
                  </div>
                  {colour && (
                    <div>
                      <Typography.Text type="secondary">Цвет: </Typography.Text>
                      <Typography.Text>{colour}</Typography.Text>
                    </div>
                  )}
                  {item.dimensionsLabel && (
                    <div>
                      <Typography.Text type="secondary">Размеры: </Typography.Text>
                      <Typography.Text>{item.dimensionsLabel}</Typography.Text>
                    </div>
                  )}
                  <div>
                    <Typography.Text type="secondary">Количество: </Typography.Text>
                    <Typography.Text>{`${item.quantity} шт.`}</Typography.Text>
                  </div>
                  </div>
                  {/* Фурнитура отделена от характеристик двери увеличенным отступом и тонкой линией. */}
                  <div
                    style={{
                      marginTop: 12,
                      paddingTop: 12,
                      borderTop: '1px solid #d9d9d9',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8,
                    }}
                  >
                  {hardware.length === 0 && <Typography.Text type="secondary">Фурнитура не добавлена</Typography.Text>}
                  {hardware.map((line) => {
                    const option = known.get(line.hardwareOptionId)
                    return (
                      <div
                        key={line.hardwareOptionId}
                        style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                        onClick={(event) => event.stopPropagation()}
                      >
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <Typography.Text>{option ? option.name : `Фурнитура № ${line.hardwareOptionId}`}</Typography.Text>
                          {option && (
                            <div>
                              <Typography.Text type="secondary">
                                {[option.article, option.colour].filter(Boolean).join(' · ')}
                              </Typography.Text>
                            </div>
                          )}
                        </div>
                        <Button
                          danger
                          size="small"
                          icon={<DeleteOutlined />}
                          aria-label="Удалить"
                          disabled={busy}
                          onClick={() => removeHardware(item, line.hardwareOptionId)}
                        />
                      </div>
                    )
                  })}
                  </div>
                </Space>
              </Card>
            )
          })}
        </div>
    </>
  )

  const catalogPanelContent = (
    <>
        {loading && <Spin />}
        {loadError && <Alert type="error" message={loadError} showIcon />}
        {actionError && <Alert type="error" message={actionError} showIcon style={{ marginBottom: 12 }} />}
        {!loading && !loadError && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space wrap align="end" size="middle">
              <div>
                <Typography.Text type="secondary">Бренд</Typography.Text>
                <div style={{ marginTop: 4 }}>
                  <Select
                    style={{ width: 200 }}
                    value={brand}
                    options={brands.map((value) => ({ value, label: value }))}
                    onChange={(value) => {
                      setBrand(value)
                      setCategory(undefined)
                      setColourFilter(undefined)
                    }}
                  />
                </div>
              </div>
              <div>
                <Typography.Text type="secondary">Элемент</Typography.Text>
                <div style={{ marginTop: 4 }}>
                  <Select
                    allowClear
                    showSearch
                    placeholder="Все элементы"
                    style={{ width: 300 }}
                    value={category}
                    options={categories.map((value) => ({ value, label: value }))}
                    onChange={(value) => {
                      setCategory(value)
                      setColourFilter(undefined)
                    }}
                  />
                </div>
              </div>
              <div>
                <Typography.Text type="secondary">Цвет</Typography.Text>
                <div style={{ marginTop: 4 }}>
                  <Select
                    allowClear
                    showSearch
                    placeholder="Все цвета"
                    style={{ width: 240 }}
                    value={colourFilter}
                    options={colours.map((value) => ({ value, label: value }))}
                    onChange={setColourFilter}
                  />
                </div>
              </div>
              <Input.Search
                allowClear
                placeholder="Поиск по модели, цвету и артикулу"
                style={{ width: 280 }}
                onChange={(event) => setSearch(event.target.value)}
              />
            </Space>

            <div>
              <Typography.Text type="secondary">Модель ({records.length})</Typography.Text>
              {/* Записи каталога — на всю ширину: одна запись = модель + один цвет + одна цена дилера и розницы;
                  кнопка «+» в строке добавляет эту запись к выбранной двери. */}
              <div style={{ maxHeight: 480, overflowY: 'auto', marginTop: 4 }}>
                <List
                  size="small"
                  bordered
                  dataSource={records}
                  locale={{ emptyText: 'Нет записей' }}
                  renderItem={(record) => {
                    const selected = record.optionId === recordId
                    return (
                      <List.Item
                        key={record.optionId}
                        onClick={() => setRecordId(record.optionId)}
                        style={{ cursor: 'pointer', background: selected ? '#e6f4ff' : undefined, gap: 16 }}
                      >
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <Typography.Text strong={selected}>{record.modelName}</Typography.Text>
                        </div>
                        <div style={{ flex: '0 1 200px', minWidth: 0, marginRight: 24 }}>
                          <Typography.Text type="secondary">Цвет</Typography.Text>
                          <div>
                            <Typography.Text>{record.colour}</Typography.Text>
                          </div>
                        </div>
                        <div style={{ flex: '0 0 100px', textAlign: 'right' }}>
                          <Typography.Text type="secondary">Цена дилер</Typography.Text>
                          <div>
                            <Typography.Text>{formatMoney(record.dealerPrice)}</Typography.Text>
                          </div>
                        </div>
                        <div style={{ flex: '0 0 100px', textAlign: 'right' }}>
                          <Typography.Text type="secondary">Цена розница</Typography.Text>
                          <div>
                            <Typography.Text>{formatMoney(record.retailPrice)}</Typography.Text>
                          </div>
                        </div>
                        <Button
                          type="primary"
                          icon={<PlusOutlined />}
                          aria-label="Добавить"
                          title="Добавить к выбранной двери"
                          style={{ flex: '0 0 auto' }}
                          disabled={busy}
                          onClick={(event) => {
                            event.stopPropagation()
                            setRecordId(record.optionId)
                            addHardware(record.optionId)
                          }}
                        />
                      </List.Item>
                    )
                  }}
                />
              </div>
            </div>
          </Space>
        )}
    </>
  )

  // Блоки — серые аккордеоны antd Collapse, как в конфигураторе остальных сервисов; оба раскрыты по умолчанию.
  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <Collapse
        defaultActiveKey={['doors', 'catalog']}
        expandIconPosition="start"
        items={[
          { key: 'doors', label: 'Позиции из корзины', children: doorsPanelContent },
          { key: 'catalog', label: 'Каталог фурнитуры', children: catalogPanelContent },
        ]}
      />
    </div>
  )
}

export default HardwareServiceScreen
