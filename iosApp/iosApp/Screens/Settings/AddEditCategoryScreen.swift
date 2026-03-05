import Shared
import SwiftUI

struct AddEditCategoryScreen: View {
    let facade: PennyWiseSharedFacade
    var editingCategory: SharedCategoryItem?
    var onSave: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var selectedColorHex: UInt = 0x78909C
    @State private var isIncome = false
    @State private var errorMessage: String?

    private var isEditing: Bool { editingCategory != nil }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private static let presetColors: [(name: String, hex: UInt)] = [
        ("Red", 0xE53935),
        ("Pink", 0xD81B60),
        ("Purple", 0x8E24AA),
        ("Deep Purple", 0x5E35B1),
        ("Indigo", 0x3949AB),
        ("Blue", 0x1E88E5),
        ("Teal", 0x00897B),
        ("Green", 0x43A047),
        ("Lime", 0x7CB342),
        ("Orange", 0xFB8C00),
        ("Deep Orange", 0xF4511E),
        ("Brown", 0x6D4C41),
        ("Blue Grey", 0x546E7A),
        ("Grey", 0x78909C),
    ]

    var body: some View {
        Form {
            Section("Name") {
                TextField("Category name", text: $name)
            }

            Section("Type") {
                Toggle("Income category", isOn: $isIncome)
            }

            Section("Color") {
                colorPicker
            }

            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(AppTypography.caption)
                }
            }
        }
        .navigationTitle(isEditing ? "Edit Category" : "New Category")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { save() }
                    .bold()
                    .disabled(!isValid)
            }
        }
        .onAppear {
            if let category = editingCategory {
                name = category.name
                isIncome = category.isIncome
                selectedColorHex = parseHex(category.colorHex)
            }
        }
    }

    // MARK: - Color Picker

    private var colorPicker: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: AppSpacing.md), count: 7), spacing: AppSpacing.md) {
            ForEach(Self.presetColors, id: \.hex) { preset in
                ZStack {
                    Circle()
                        .fill(Color(hex: preset.hex))
                        .frame(width: 36, height: 36)

                    if selectedColorHex == preset.hex {
                        Circle()
                            .strokeBorder(.white, lineWidth: 2)
                            .frame(width: 36, height: 36)
                        Image(systemName: "checkmark")
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                    }
                }
                .onTapGesture {
                    selectedColorHex = preset.hex
                }
            }
        }
        .padding(.vertical, AppSpacing.sm)
    }

    // MARK: - Actions

    private func save() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }

        let colorString = String(format: "#%06X", selectedColorHex)
        let viewModel = CategoriesViewModel(facade: facade)

        if let category = editingCategory {
            let success = viewModel.updateCategory(
                id: category.id,
                name: trimmedName,
                colorHex: colorString,
                isIncome: isIncome
            )
            if !success {
                errorMessage = viewModel.errorMessage ?? "Failed to update"
                return
            }
        } else {
            let success = viewModel.createCategory(
                name: trimmedName,
                colorHex: colorString,
                isIncome: isIncome
            )
            if !success {
                errorMessage = viewModel.errorMessage ?? "Failed to create"
                return
            }
        }

        onSave?()
        dismiss()
    }

    private func parseHex(_ value: String) -> UInt {
        let hex = value
            .replacingOccurrences(of: "#", with: "")
            .replacingOccurrences(of: "0x", with: "")
            .replacingOccurrences(of: "0X", with: "")
        let trimmed = hex.count > 6 ? String(hex.suffix(6)) : hex
        return UInt(trimmed, radix: 16) ?? 0x78909C
    }
}
