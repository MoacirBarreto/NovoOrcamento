package devandroid.moacir.Lume

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import devandroid.moacir.Lume.database.AppDatabase
import devandroid.moacir.Lume.databinding.ActivityNovoLancamentoBinding
import devandroid.moacir.Lume.model.Agenda
import devandroid.moacir.Lume.model.Categoria
import devandroid.moacir.Lume.model.Lancamento
import devandroid.moacir.Lume.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class NovoLancamentoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNovoLancamentoBinding.inflate(layoutInflater) }
    private val db by lazy { AppDatabase.getDatabase(this) }

    private var categorias: List<Categoria> = emptyList()
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()

    private var isAgenda: Boolean = false
    private var lancamentoParaEditar: Lancamento? = null
    private var agendaParaEditar: Agenda? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)

        configurarInterfaceInicial()
        carregarDadosIniciais()
    }

    private fun configurarInterfaceInicial() {
        // Data padrão de hoje
        atualizarTextoData(Calendar.getInstance())

        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        configurarCheckRepetir()
    }

    private fun configurarCheckRepetir() {
        binding.chkRepetir.setOnCheckedChangeListener { _, isChecked ->
            val visibilidade = if (isChecked) View.VISIBLE else View.GONE
            binding.edtQtdMeses.visibility = visibilidade
            binding.txtMesesLabel.visibility = visibilidade
            if (!isChecked) binding.edtQtdMeses.setText("")
        }
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            val idRecebido = intent.getIntExtra("LANCAMENTO_ID", -1)

            // 1. Visibilidade do container de repetição (apenas em nova agenda)
            val mostrarContainerRepetir = isAgenda && idRecebido == -1
            binding.containerRepetir.visibility = if (mostrarContainerRepetir) View.VISIBLE else View.GONE

            // 2. Busca Categorias (Performance: busca única)
            categorias = withContext(Dispatchers.IO) { db.orcamentoDao().listarCategorias() }

            // 3. Configura Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    if (idRecebido == -1 && binding.rbDespesa.isChecked) {
                        binding.edtDescricao.setText(categorias[position].nome)
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }

            // 4. Fluxo Edição vs Novo
            if (idRecebido != -1) {
                binding.txtTituloTela.text = if (isAgenda) "Editar Agenda" else "Editar Lançamento"
                carregarParaEdicao(idRecebido)
            } else {
                binding.txtTituloTela.text = if (isAgenda) "Nova Agenda" else "Novo Lançamento"
                atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
            }
        }
    }

    private suspend fun carregarParaEdicao(id: Int) {
        withContext(Dispatchers.IO) {
            if (isAgenda) {
                db.agendaDao().buscarPorId(id)?.let {
                    agendaParaEditar = it
                    withContext(Dispatchers.Main) {
                        preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
                    }
                }
            } else {
                db.orcamentoDao().buscarPorId(id)?.let {
                    lancamentoParaEditar = it
                    withContext(Dispatchers.Main) {
                        preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
                    }
                }
            }
        }
    }

    private fun preencherCampos(desc: String, valor: Double, tipo: TipoLancamento, data: Long, catId: Int) {
        with(binding) {
            val valorFormatado = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)
            edtValor.setText(valorFormatado)
            edtDescricao.setText(desc)

            if (tipo == TipoLancamento.RECEITA) rbReceita.isChecked = true else rbDespesa.isChecked = true
            atualizarVisibilidadeCategoria(tipo == TipoLancamento.DESPESA)

            val cal = Calendar.getInstance().apply { timeInMillis = data }
            atualizarTextoData(cal)

            val posicao = categorias.indexOfFirst { it.id == catId }
            if (posicao != -1) spinnerCategorias.setSelection(posicao)
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val cleanValor = binding.edtValor.text.toString().replace(Regex("[^0-9]"), "")

            if (descricao.isBlank() || cleanValor.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val valorFinal = cleanValor.toDouble() / 100
            val tipo = if (binding.rbReceita.isChecked) TipoLancamento.RECEITA else TipoLancamento.DESPESA
            val catId = if (tipo == TipoLancamento.DESPESA) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.equals("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) salvarAgenda(descricao, valorFinal, tipo, catId)
                    else salvarLancamento(descricao, valorFinal, tipo, catId)
                }
                Toast.makeText(this@NovoLancamentoActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun salvarAgenda(desc: String, valor: Double, tipo: TipoLancamento, catId: Int) {
        if (agendaParaEditar != null) {
            db.agendaDao().upsertAgenda(agendaParaEditar!!.copy(
                descricao = desc, valor = valor, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
            ))
        } else {
            val numRepeticoes = binding.edtQtdMeses.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val cal = Calendar.getInstance().apply { timeInMillis = dataSelecionadaMillis }
            for (i in 0 until numRepeticoes) {
                val descFinal = if (numRepeticoes > 1) "$desc (${i + 1}/$numRepeticoes)" else desc
                db.agendaDao().upsertAgenda(Agenda(
                    id = 0, descricao = descFinal, valor = valor, data = cal.timeInMillis, categoriaID = catId, tipo = tipo
                ))
                cal.add(Calendar.MONTH, 1)
            }
        }
    }

    private suspend fun salvarLancamento(desc: String, valor: Double, tipo: TipoLancamento, catId: Int) {
        val lancamento = lancamentoParaEditar?.copy(
            descricao = desc, valor = valor, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
        ) ?: Lancamento(
            id = 0, descricao = desc, valor = valor, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
        )
        db.orcamentoDao().upsertLancamento(lancamento)
    }

    private fun configurarCampoData() {
        // Configura tanto o clique no campo quanto no ícone
        binding.edtData.setOnClickListener {
            val builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecione a Data")
                // Abre o calendário na data que já está no campo
                .setSelection(dataSelecionadaMillis)
                // Usa o tema do Material Components (estilo Gráficos)
                .setTheme(com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)

            val picker = builder.build()

            picker.addOnPositiveButtonClickListener { selection ->
                // O MaterialDatePicker retorna a data em UTC (pode dar erro de 1 dia de diferença)
                // Ajustamos para o fuso horário local do celular
                val timeZone = TimeZone.getDefault()
                val offset = timeZone.getOffset(selection)
                val dataLocal = selection - offset

                val cal = Calendar.getInstance().apply {
                    timeInMillis = dataLocal
                }

                atualizarTextoData(cal)
            }
            picker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun atualizarTextoData(calendario: Calendar) {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        binding.edtData.setText(formato.format(calendario.time))
        dataSelecionadaMillis = calendario.timeInMillis
    }
    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val isDespesa = checkedId == R.id.rbDespesa
            atualizarVisibilidadeCategoria(isDespesa)
            if (lancamentoParaEditar == null && agendaParaEditar == null) {
                if (!isDespesa) binding.edtDescricao.setText("Receita")
                else {
                    val pos = binding.spinnerCategorias.selectedItemPosition
                    if (pos != -1) binding.edtDescricao.setText(categorias[pos].nome)
                }
            }
        }
    }

    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        val visibilidade = if (isDespesa) View.VISIBLE else View.GONE
        binding.lblCategoria.visibility = visibilidade
        binding.spinnerCategorias.visibility = visibilidade
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")
                    if (cleanString.isNotEmpty()) {
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cleanString.toDouble() / 100)
                        current = formatted
                        binding.edtValor.setText(formatted)
                        binding.edtValor.setSelection(formatted.length)
                    }
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}
